# user-service walkthrough

This explains each component of the user-service in the order a request flows through it, then covers configuration, the build, and tests. For each component: **why** we need it, the **problem** it solves, **how** it works, **why this approach**, and **interviewer questions**.

A request's path through the service:

```
HTTP request
  -> GlobalExceptionHandler (wraps everything below; turns exceptions into problem+json)
  -> CustomerController        validate input (@Valid), pick status code
  -> CustomerService           business rules, transaction boundary
  -> CustomerMapper            DTO <-> entity
  -> CustomerRepository        Spring Data JPA -> Hibernate -> JDBC
  -> PostgreSQL                schema created and versioned by Flyway
```

---

## 1. Flyway migrations (`db/migration/V1__*.sql`, `V2__*.sql`)

**Why.** The database schema must be created and evolved in a controlled, repeatable way on every environment.

**Problem.** Hand-run SQL scripts drift between laptops, test and production. Hibernate's `ddl-auto=update` cannot rename columns or migrate data, has no history, and can make destructive changes silently.

**How.** On startup, Flyway looks at the `flyway_schema_history` table, finds which versioned scripts have not yet run, runs them in order inside transactions (PostgreSQL supports transactional DDL), and records a checksum for each. If someone edits an already-applied script, the checksum mismatch stops startup. Hibernate then runs with `ddl-auto: validate`, which fails fast if an entity no longer matches the schema.

**Why this approach.** SQL-first migrations let you use PostgreSQL features directly: the `CHECK` constraints (`email = LOWER(email)`, allowed status values, country format) enforce rules even against code that bypasses the application. Constraints are named (`uk_customers_email`) so errors and future migrations can refer to them.

Notice `idx_addresses_customer_id`. PostgreSQL does *not* create indexes for foreign-key columns automatically; without it, "all addresses for customer X" is a full table scan.

**Interviewer questions.**
- How do you change a column in production without downtime? (Expand/contract: add the new column, dual-write, backfill, switch reads, then drop the old one in a later release.)
- Flyway vs Liquibase?
- What happens if two instances start at the same time? (Flyway takes a database lock so only one migrates.)
- Why never edit an applied migration? (Checksum validation fails; fix forward with a new version.)

---

## 2. Entities (`Customer`, `Address`, enums)

**Why.** JPA entities map Java objects to table rows so we don't hand-write SQL for basic persistence.

**Problem.** Without an ORM, every read/write needs manual JDBC and row mapping. Without discipline, entities become anaemic bags of setters that let any caller put an object into an invalid state.

**How.**
- `@GeneratedValue(strategy = GenerationType.UUID)`: Hibernate assigns the UUID when the entity is persisted, before the INSERT.
- `@Enumerated(EnumType.STRING)`: stores `"ACTIVE"`, not `0`.
- `@Version`: Hibernate adds `WHERE version = ?` to every UPDATE and increments it. If another transaction updated the row first, zero rows match and an `OptimisticLockingFailureException` is raised, which we turn into a 409.
- `@CreatedDate` / `@LastModifiedDate` with `AuditingEntityListener`: Spring Data fills these on persist and update.
- `@ManyToOne(fetch = LAZY)` on `Address.customer`: loading an address does not load its customer unless you touch it.

**Why this approach.**
- `Customer` has **no public setters**. Changes go through `updateProfile()`, `deactivate()` and the constructor, which always sets `ACTIVE`. The rule lives in one place.
- `Address` uses field-level `@Setter` because it has no lifecycle rules. The `id` and `customer` have no setters.
- The relationship is **unidirectional**. There is no `List<Address>` on `Customer`, which avoids accidental loading and cascade surprises. When we need addresses we ask the repository.
- **No `@Data`, no custom `equals/hashCode`.** Lombok's `@Data` generates `equals/hashCode` from all fields, which breaks when Hibernate generates the id or when lazy fields are involved, and its `toString` can trigger lazy loading. Default object identity is correct for how we use entities.
- The protected no-arg constructor exists only because JPA requires it.

**Interviewer questions.**
- What is the N+1 query problem, and how do you fix it? (Fetch joins, `@EntityGraph`, batch fetching.)
- Optimistic vs pessimistic locking: when would you use `SELECT ... FOR UPDATE`?
- Why is `FetchType.EAGER` usually a bad default?
- How should you implement `equals/hashCode` on a JPA entity, if at all?
- What is the persistence context / first-level cache?

---

## 3. Repositories (`CustomerRepository`, `AddressRepository`)

**Why.** Data access without boilerplate DAO classes.

**Problem.** Hand-written DAOs repeat the same CRUD, paging and query code for every entity.

**How.** Spring Data generates an implementation of the interface at startup. Method names are parsed into queries: `findAllByStatus(status, pageable)` becomes `SELECT ... WHERE status = ? ORDER BY ... LIMIT ... OFFSET ...` plus a count query for the page metadata. `findAllByCustomerId` navigates into `address.customer.id` and uses the FK column without a join.

**Why this approach.** `findByIdAndCustomerId` is a small but important security habit: an address is always looked up *together with its owner*, so `/customers/A/addresses/{id-of-B's-address}` returns 404 instead of B's data. This is how you avoid IDOR (insecure direct object reference) bugs.

**Interviewer questions.**
- Derived queries vs `@Query` vs Specifications vs Criteria API: when would you use each?
- What does `Page` cost compared to `Slice`? (`Page` runs an extra COUNT query.)
- Offset vs keyset (cursor) pagination for large tables?
- What's the difference between `save`, `saveAndFlush` and plain dirty checking?

---

## 4. DTOs (`dto/*` records)

**Why.** The API needs its own contract, separate from the database model.

**Problem.** Returning entities directly exposes internal fields (`passwordHash`, `version`), couples the API to the schema (renaming a column breaks clients), invites lazy-loading exceptions during JSON serialisation, and lets clients mass-assign fields they shouldn't (e.g. `status`) on input.

**How.** Java `record`s: immutable, concise, with Bean Validation annotations on the components. There are separate request and response types, so the shape of what you send differs from what you get back.

**Why this approach.**
- `RegisterCustomerRequest` **overrides `toString()`**. A record's generated `toString()` includes every field, so logging the request would log the password.
- `UpdateCustomerRequest` has **no email field**. Changing a login identity needs verification, which belongs with authentication.
- Passwords are limited to 72 characters because BCrypt only uses the first 72 bytes.
- `PageResponse` is our own envelope rather than Spring's `PageImpl`, whose JSON shape is not a stable contract.
- The enums (`CustomerStatus`, `AddressType`) are reused from the entity package for simplicity. Stricter designs define separate API enums so the database and API can evolve independently.

**Interviewer questions.**
- Why not expose entities? (List the four problems above.)
- What's a mass-assignment vulnerability?
- Records vs Lombok `@Value` for DTOs?

---

## 5. Mappers (`CustomerMapper`, `AddressMapper`)

**Why.** Something has to convert between DTOs and entities.

**Problem.** Mapping code scattered through services and controllers is duplicated and easy to get wrong.

**How.** Plain Spring `@Component`s with explicit methods. `toEntity` for creation, `applyRequest` for updates, `toResponse` for output.

**Why this approach.** Hand-written so that every mapping is visible while learning. Business decisions (normalising the email, hashing the password) stay in the service; the mapper only copies fields. Because they are injectable components, they can be replaced by MapStruct later, which generates equivalent code at compile time, without changing any caller.

**Interviewer questions.**
- MapStruct vs ModelMapper? (MapStruct generates plain code at compile time and fails the build on unmapped fields; ModelMapper uses reflection at runtime and fails silently.)
- Where should mapping happen: controller or service? (Here the service returns DTOs, so entities never escape the transaction.)

---

## 6. Services (`CustomerService`, `AddressService`)

**Why.** The home of business rules and transaction boundaries.

**Problem.** Rules in controllers can't be reused or unit-tested easily; rules in entities can't reach repositories or other components.

**How.**
- `@Transactional(readOnly = true)` on the class, `@Transactional` on writing methods. A read-only transaction lets Hibernate skip dirty checking and lets the driver/database optimise.
- `register`: normalise email with `Locale.ROOT`, check uniqueness, hash with BCrypt, save. The pre-check gives a friendly 409; the database `UNIQUE` constraint is the real guarantee if two requests race (that case surfaces as `DataIntegrityViolationException`, also a 409).
- `updateCustomer`: loads the entity, calls `updateProfile`, then `saveAndFlush`. The flush makes Hibernate write now, which fires `@LastModifiedDate` and bumps `@Version`, so the response shows the new `updatedAt`. Without the flush, the write would happen at commit, *after* the response was built.
- `deactivateCustomer`: idempotent soft delete.
- Shared rule in both services: **inactive customers are read-only**.
- Logs contain ids only, never names, emails or phone numbers.

**Why this approach.** Concrete classes rather than `interface CustomerService` + `CustomerServiceImpl`. With a single implementation, the interface adds ceremony without benefit; Mockito mocks concrete classes, and Spring proxies them with CGLIB for `@Transactional`. Introduce an interface when a second implementation is real.

Constructor injection (`@RequiredArgsConstructor` on `final` fields) makes dependencies explicit and lets tests construct the service with `new`.

**Interviewer questions.**
- Why doesn't `@Transactional` work when a method calls another method in the same class? (Self-invocation bypasses the Spring proxy.)
- What does `readOnly = true` actually do?
- Which exceptions roll back a transaction by default? (Unchecked ones; checked exceptions don't unless configured.)
- Why check `existsByEmail` if the database has a unique constraint anyway?
- Why BCrypt and not SHA-256? (Deliberately slow and salted; SHA-256 is fast, which helps attackers brute-force.)

---

## 7. Controllers (`CustomerController`, `AddressController`)

**Why.** The HTTP adapter.

**Problem.** Without a thin, consistent HTTP layer, status codes, validation and URL design become inconsistent.

**How.** `@Valid` triggers Bean Validation before the method runs. `ResponseEntity.created(location)` returns 201 with a `Location` header built from the current request URL. `@ResponseStatus(NO_CONTENT)` for deletes. `@PageableDefault` plus Spring Data's web support turns `?page=&size=&sort=` into a `Pageable`, with `max-page-size: 100` in configuration. `@ParameterObject` makes Swagger show those as separate query parameters.

**Why this approach.** Addresses are a nested sub-resource because they cannot exist without a customer. Controllers contain no business logic and never touch repositories.

**Interviewer questions.**
- What does `@RestController` add over `@Controller`?
- Why return 201 with a `Location` header?
- How would you restrict which fields can be used for sorting? (Validate against an allow-list; relevant because sorting on an arbitrary column can leak information.)

---

## 8. Global exception handling (`GlobalExceptionHandler`)

**Why.** Every error should have the same shape and the right status code.

**Problem.** Without it, Spring returns a mix of default error JSON, stack traces and HTML pages, and each controller invents its own error format.

**How.** `@RestControllerAdvice` intercepts exceptions from every controller. Each custom exception maps to a status code. It extends `ResponseEntityExceptionHandler`, which already handles Spring MVC's own errors (malformed JSON, bad UUIDs, wrong content type) as Problem Details. We override `handleMethodArgumentNotValid` to list *every* invalid field. Spring picks the most specific handler, so the `Exception.class` handler is only a last resort; it logs the stack trace and returns a generic message.

**Why this approach.** RFC 9457 Problem Details is an IETF standard, built into Spring 6, so clients and gateways can rely on one format. Database error messages are logged but never returned, because they reveal table and constraint names.

**Interviewer questions.**
- `@ControllerAdvice` vs `@RestControllerAdvice`?
- How does Spring choose between multiple matching `@ExceptionHandler`s?
- Why not return `e.getMessage()` for every exception?
- 400 vs 422 vs 409: how do you choose?

---

## 9. Configuration (`application.yml`, `configuration/*`)

**Why.** Behaviour that varies by environment must live outside the code.

**Problem.** Hard-coded URLs and passwords end up in Git and force a rebuild for every environment.

**How.**
- Credentials are read from environment variables (`${USER_DB_USERNAME}`) with **no defaults**, so a missing secret fails loudly instead of silently using a known password. Only the local database URL has a default.
- `open-in-view: false` closes the persistence context when the service method returns. Any lazy loading outside a transaction fails immediately in development instead of issuing hidden queries in production.
- `server.shutdown: graceful` lets in-flight requests finish when the container is stopped.
- Actuator exposes `health` (with Kubernetes-style `liveness`/`readiness` groups), `info` and `metrics` only.
- `JpaAuditingConfig` is a separate class so `@WebMvcTest` slices don't try to start JPA.
- `PasswordEncoderConfig` uses `spring-security-crypto` alone, which gives BCrypt without switching on Spring Security's filter chain.

**Interviewer questions.**
- What is open-session-in-view and why disable it?
- How would you manage secrets in production? (A secrets manager or orchestrator secrets, injected as environment variables or mounted files; never in the image or repository.)
- Liveness vs readiness probes?

---

## 10. Tests

| Test | Type | What it proves | Needs Docker |
|---|---|---|---|
| `CustomerServiceTest`, `AddressServiceTest` | Unit (JUnit 5 + Mockito) | Business rules: normalisation, hashing, duplicates, inactive = read-only, idempotent delete, ownership checks | No |
| `CustomerControllerTest` | Web slice (`@WebMvcTest`) | HTTP contract: 201 + Location, 400 with every field error, 404 problem+json, no password in responses | No |
| `CustomerRepositoryIntegrationTest` | JPA slice (`@DataJpaTest` + Testcontainers) | Real Flyway migrations, auditing, unique and check constraints, derived queries | Yes |
| `CustomerApiIntegrationTest` | Full (`@SpringBootTest` + Testcontainers) | The whole stack end to end for key flows | Yes |

This is the test pyramid: many fast unit tests, fewer slice tests, a handful of full integration tests. `@ServiceConnection` wires the container's URL and credentials into Spring automatically. `@MockitoBean` replaces the deprecated `@MockBean`.

**Why real PostgreSQL instead of H2?** H2 does not behave like PostgreSQL: different SQL dialect, no `TIMESTAMPTZ` semantics, different constraint behaviour. A test that passes on H2 can fail in production.

**Interviewer questions.**
- Unit vs slice vs integration tests: what does each give you?
- Why is mocking the repository fine in a service test but not enough on its own?
- What does `@DataJpaTest` roll back, and why?

---

## 11. Docker

**Why.** The service should run the same way everywhere.

**How.** A multi-stage `Dockerfile`: a Maven + JDK image builds the jar, and only the jar is copied into a JRE-only image that runs as a non-root user. `-XX:MaxRAMPercentage=75` sizes the heap from the container's memory limit. Tests are skipped inside the image build because they run in CI and Testcontainers needs Docker.

**Interviewer questions.**
- Why multi-stage builds? (Smaller image, no build tools in production.)
- Why not run as root?
- How does the JVM respect container memory limits?
