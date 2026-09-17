# Change log

Every change to the codebase: **what** changed, **where**, **why**, and **what the result is**.

Newest entries at the top. Each entry is self-contained, so you can read one without the others.

---

## Seed data — a shop that looks like a shop

**Date:** Day 3

### What changed

| File | New / Changed | What it does |
|---|---|---|
| `scripts/seed-data.ps1` | **New** | Fills the platform with realistic data through the real APIs |
| `.gitignore` | Changed | `.vscode/` is now committed (no secrets in it; it gives anyone cloning the repo one-click launches) |

### Why

The database held only test-rig data — `verify-alice`, one t-shirt, `correct-horse-battery`.
Fine for proving rules; useless for demos, screenshots, or the feel of a working product.

### The important decision: seed through the APIs, not the database

Inserting rows directly would bypass everything built so far — passwords would need hashing by
hand, security rules would never run, prices would not be validated. Going through the APIs
means the seed data is created exactly the way real traffic would be, and doubles as an
end-to-end exercise of the whole platform.

### What it creates

- **Meera Joshi**, store manager — the one ADMIN, promoted via a single database `UPDATE`
- **8 customers** with realistic names, emails, phone numbers and addresses in real Indian
  cities (Indore, Rewa, Ahmedabad, Delhi, Chennai, Mumbai, Hyderabad, Bhopal)
- **6 categories, 24 products** — recognisable items with plausible INR prices
- **~9 orders** spread across the whole lifecycle: delivered, shipped, paid, pending, and one
  cancelled by its customer while still pending

Re-runnable: existing customers are logged into rather than recreated; existing categories and
SKUs are reused. Nothing is ever deleted, including the old verify data — cleanup is a
decision to make together, not something a script does silently.

---

## Documentation set — end-to-end PDFs and the beginner's guide

**Date:** Day 3

### What changed

| File | New / Changed | What it does |
|---|---|---|
| `docs/security-walkthrough.md` | **New** | Phase 2 implementation walkthrough with the full source of every security class and the reasoning behind each |
| `docs/beginners-guide.md` | **New** | The whole project in plain language: every tool explained, from-zero setup, the daily routine, a decoded table of every real error hit so far, and a glossary |
| Four PDFs (generated) | **New** | Documentation, Change Log, Code Walkthrough, Beginner's Guide |

### Why

A milestone (Phase 1 verified + Phase 2 complete) is the right moment to freeze documentation:
the reasoning is still fresh, and the next phase (Git, then Kafka) builds on top of it.

The beginner's guide exists because the project's own history is its best teaching material —
every error in its troubleshooting table genuinely occurred during the build, with the real
cause and the real fix.

### Result

The `docs/` folder is the single source of truth (Markdown, versionable); the PDFs are
generated artifacts for reading and sharing. When Git arrives, the Markdown goes in the repo
and PDFs can be regenerated at any milestone.

---

## Phase 2, Step 4 — Rewriting the verification script for a secured platform

**Date:** Day 3

### What changed

| File | New / Changed | What it does |
|---|---|---|
| `scripts/verify-phase2.ps1` | **New** | End-to-end check with authentication and authorization |
| `scripts/verify-phase1.ps1` | Changed | Now exits immediately with a pointer to the new script |
| `README.md` | Changed | Points at the new script and lists what it covers |

### Why

`verify-phase1.ps1` sends no tokens, so after Steps 2 and 3 every protected call returned 401.
That is correct behaviour, not a regression — but it left no single command to confirm the
platform works.

The old script is kept rather than deleted: comparing the two shows exactly what securing a
platform changes about how a client must behave.

### What the new script does that the old one could not

- Registers two customers and **promotes one to ADMIN** with a direct `UPDATE`, because there
  is deliberately no endpoint for granting roles
- Demonstrates that the **pre-promotion token still says CUSTOMER** — a token is a snapshot,
  and changing the database does not reach back into tokens already issued
- Sends a **tampered token** (last six characters replaced) and confirms it is refused
- Checks each rule from both sides: the customer who may, and the customer who may not
- Confirms that a refused cancel **changed nothing** — proving the ownership check runs before
  the state change, not after

### Result

One command reports on the whole platform again. Around 35 checks across seven groups, each
with a short note explaining what it proves.

### Known consequences

- The script needs the `ecommerce-postgres` container running, because promoting to ADMIN goes
  through `docker exec`.
- It creates three customers, one category and two products per run, and deletes nothing. Test
  data accumulates; that is deliberate, since deleting data is never done without asking.

---

## Phase 2, Step 3 — Enforcing tokens in product-service and order-service

**Date:** Day 3

### What changed

**product-service**

| File | New / Changed | What it does |
|---|---|---|
| `pom.xml` | Changed | Added `spring-boot-starter-security`, JJWT, `spring-security-test` |
| `application.yml` | Changed | New `jwt:` block — the **same secret** user-service signs with |
| `configuration/JwtProperties.java` | **New** | Binds `jwt.secret` and `jwt.issuer`. No expiry: this service does not issue tokens |
| `configuration/JwtService.java` | **New** | Verify only. There is deliberately no method to create a token |
| `configuration/JwtAuthenticationFilter.java` | **New** | Identifies the caller on every request |
| `configuration/SecurityConfig.java` | **New** | Catalogue reads public, everything else authenticated |
| `configuration/SecurityProblemHandlers.java` | **New** | 401/403 as problem JSON |
| `configuration/OpenApiConfig.java` | Changed | Authorize button in Swagger |
| `controller/ProductController.java`, `CategoryController.java` | Changed | `@PreAuthorize("hasRole('ADMIN')")` on create, update, delete |
| `exception/GlobalExceptionHandler.java` | Changed | Handlers for `AccessDeniedException` (403) and `AuthenticationException` (401) |

**order-service** — the same six security files, plus:

| File | New / Changed | What it does |
|---|---|---|
| `client/TokenRelayInterceptor.java` | **New** | Copies the caller's bearer token onto every outgoing Feign call |
| `client/FeignClientConfig.java` | Changed | Registers the interceptor |
| `client/ServiceErrorDecoder.java` | Changed | A downstream 401/403 becomes `DownstreamAuthException`, not a 500 |
| `exception/DownstreamAuthException.java` | **New** | Mapped to 401 |
| `controller/OrderController.java` | Changed | `@PreAuthorize` on place and status; `@PostAuthorize` on read; explicit check for cancel |
| `controller/CustomerOrderController.java` | Changed | Class-level rule: your own history, or any if ADMIN |
| `service/OrderService.java` | Changed | `cancelOrder` now takes the caller's id and admin flag |
| `service/OrderServiceTest.java` | Changed | Updated signature; two new ownership tests |

No database migrations.

### Why

Both services were still completely open. Anyone could create products, change prices, or read
any customer's entire order history.

There was also a problem created by the previous step: order-service calls user-service for the
customer and address, and those endpoints had just become protected. Without token forwarding,
placing an order would have started failing with 401 from a dependency.

### The decision: token propagation

When order-service calls the others, it **forwards the customer's own token** rather than using
a service identity of its own.

*Why:* user-service then sees "Jane asking about Jane", and the ownership rule written in Step 2
passes with no special case for service callers. Identity survives the whole chain, which also
means logs can trace one customer's action across three services.

*The cost, stated plainly:* a stolen token works against every service, not only the one it was
presented to. The alternative — each service holding its own machine identity with narrow
scopes — is what larger systems do, and is the natural upgrade.

*A real limitation:* `TokenRelayInterceptor` reads the request currently being served on this
thread. That works because Feign calls happen on the same thread as the inbound request. If this
service moves to async or reactive calls, the context will not follow and this class needs
rethinking.

### Three techniques, and why each was used where it was

| Technique | Used for | Why not the others |
|---|---|---|
| `@PreAuthorize` | Place order, list history, admin writes | The rule only needs the URL or request body, both available before the method runs |
| `@PostAuthorize` | `GET /orders/{id}` | The owner is in the database, not the URL, so the order must be loaded first. Safe **only because this is a read** |
| Explicit check in the service | `PATCH /orders/{id}/cancel` | Also needs the loaded order, but cancelling **changes data**. `@PostAuthorize` would let the cancel happen and only then refuse to show it |

That third row is the one worth remembering: `@PostAuthorize` on a mutating method is a real bug,
not a style preference.

### Result

**product-service**

| Request | Before | After |
|---|---|---|
| `GET /products` with no token | 200 | 200 — browsing stays public |
| `GET /products/{id}` with no token | 200 | 200 |
| `POST /products` with no token | 201 | **401** |
| `POST /products` as CUSTOMER | 201 | **403** |
| `POST /products` as ADMIN | 201 | 201 |
| `GET /products/lookup` with no token | 200 | **401** |

**order-service**

| Request | Before | After |
|---|---|---|
| Any endpoint with no token | worked | **401** |
| `POST /orders` for yourself | 201 | 201 — token forwarded downstream |
| `POST /orders` for another customer | 201 | **403** |
| `GET /orders/{id}` for someone else's order | 200 | **403** |
| `GET /customers/{other}/orders` | 200 | **403** |
| `PATCH /orders/{id}/status` as CUSTOMER | 200 | **403** — fulfilment is staff work |
| `PATCH /orders/{id}/cancel` on someone else's order | 200 | **403**, and nothing is changed |

### Known consequences

- **`verify-phase1.ps1` is now broken** — it sends no tokens. It will be rewritten to log in first.
- **The shared secret is a real weakness.** HS256 means every service that can *verify* a token
  can also *mint* one; a compromised product-service could issue itself an admin token. The fix
  is RS256 — user-service holds a private key, the others hold only the public key. Deferred
  deliberately, and worth naming in an interview.
- **There are no ADMIN users** unless you create one: `UPDATE customers SET role = 'ADMIN'
  WHERE email = '...'`, then log in again for a fresh token.
- **All three services need the same `JWT_SECRET`** in `.env`, and the same `issuer` value.
  A mismatch rejects every token with no obvious clue why.

---

## Phase 2, Step 2 — Enforcing tokens in user-service

**Date:** Day 3

### What changed

| File | New / Changed | What it does |
|---|---|---|
| `user-service/pom.xml` | Changed | Swapped `spring-security-crypto` for the full `spring-boot-starter-security`; added `spring-security-test` |
| `configuration/JwtAuthenticationFilter.java` | **New** | Reads the `Authorization` header on every request, verifies the token, records who the caller is |
| `configuration/SecurityConfig.java` | **New** | Decides which endpoints need a token; wires the filter into the chain |
| `configuration/SecurityProblemHandlers.java` | **New** | Makes 401/403 responses look like every other error (problem JSON) |
| `configuration/OpenApiConfig.java` | Changed | Adds the **Authorize** button to Swagger UI |
| `service/JwtService.java` | Changed | Added `parseToken()` — previously it could only *create* tokens, not *verify* them |
| `controller/CustomerController.java` | Changed | Added `@PreAuthorize` rules to five endpoints |
| `controller/AddressController.java` | Changed | Added one `@PreAuthorize` rule at class level (applies to all its methods) |
| `exception/GlobalExceptionHandler.java` | Changed | Added handlers for `AccessDeniedException` (403) and `AuthenticationException` (401) |
| `controller/SecurityRulesTest.java` | **New** | Tests the authorization rules as behaviour |
| `controller/CustomerControllerTest.java` | Changed | Imports the security beans; two tests now run as an admin |
| `controller/AuthControllerTest.java` | Changed | Imports the security beans |
| `integration/CustomerApiIntegrationTest.java` | Changed | Logs in and sends real tokens; added an "other customer is forbidden" test |

No database migration. No change to product-service or order-service.

### Why

Every endpoint was reachable by anyone. `GET /api/v1/customers` returned every customer's
email and phone number with no credentials at all, and `DELETE` would deactivate any of them.

A token that nothing checks is just a string. Step 1 made tokens; this step makes them matter.

### How it works, briefly

1. A request arrives with `Authorization: Bearer <token>`
2. `JwtAuthenticationFilter` runs **before any controller** — it verifies the signature and
   expiry locally (no database, no call to another service) and writes the caller's id and role
   into Spring Security's per-request `SecurityContext`
3. `SecurityConfig` decides whether this path is allowed to proceed unauthenticated
4. `@PreAuthorize` on the controller method applies the finer rule

The filter itself never rejects anything. It only *identifies*. Rejecting is
`SecurityConfig`'s job. Keeping those separate is what lets one filter serve both public and
protected endpoints.

### Two decisions worth remembering

**Default deny.** The config protects `anyRequest()` and then names the exceptions. The reverse
arrangement — listing what to protect — means any endpoint added later ships wide open, silently.
This way a forgotten endpoint fails closed: annoying, visible, fixed in a minute.

**Ownership checks.** `#id == authentication.principal` compares the id in the URL to the id in
the token. Without it, any logged-in customer could read any other customer by changing the
number in the URL. That bug class is called **IDOR** (insecure direct object reference) and is
consistently the most common real-world API vulnerability.

### A bug this step had to avoid

`@PreAuthorize` throws `AccessDeniedException` **inside** the controller call. The existing
catch-all `@ExceptionHandler(Exception.class)` would have caught it and returned **500** instead
of **403** — misleading to the caller, and it would hide a genuine security signal in the logs.

An explicit handler now sits above the catch-all, and `SecurityRulesTest` fails if it is removed.

### Result

| Request | Before | After |
|---|---|---|
| `GET /customers` with no token | 200, full list | **401** |
| `GET /customers` as a CUSTOMER | 200, full list | **403** |
| `GET /customers` as an ADMIN | 200 | 200 |
| `GET /customers/{own-id}` as that customer | 200 | 200 |
| `GET /customers/{someone-else}` as a customer | 200 | **403** |
| `POST /auth/login` | 200 | 200 (still public) |
| `POST /customers` (register) | 201 | 201 (still public) |
| `/swagger-ui.html`, `/actuator/health` | 200 | 200 (still public) |

### Known consequences

- **`verify-phase1.ps1` now fails** on user-service calls, because it sends no tokens. This is
  correct behaviour, not a regression. The script is updated once all three services are secured.
- **product-service and order-service are still open.** Anyone can create products or read orders.
- **Changing a role in the database does not change tokens already issued.** Promote yourself to
  ADMIN and you must log in again; the old token still says CUSTOMER. Same reason a banned user
  keeps working until their token expires.

---

## Phase 2, Step 1 — Login and token issuing in user-service

**Date:** Day 3

### What changed

| File | New / Changed | What it does |
|---|---|---|
| `user-service/pom.xml` | Changed | Added the JJWT library (api compile-scope, impl + jackson runtime) |
| `db/migration/V3__add_role_to_customers.sql` | **New** | Adds a `role` column to `customers` |
| `entity/Role.java` | **New** | `CUSTOMER` or `ADMIN` |
| `entity/Customer.java` | Changed | New `role` field; the constructor always sets `CUSTOMER` |
| `configuration/JwtProperties.java` | **New** | Type-safe binding of the `jwt.*` settings |
| `service/JwtService.java` | **New** | Builds signed tokens |
| `service/AuthService.java` | **New** | Verifies email + password, issues a token |
| `dto/LoginRequest.java`, `dto/TokenResponse.java` | **New** | The login contract |
| `controller/AuthController.java` | **New** | `POST /api/v1/auth/login` |
| `exception/InvalidCredentialsException.java` | **New** | Mapped to 401 |
| `repository/CustomerRepository.java` | Changed | Added `findByEmail` |
| `application.yml` | Changed | New `jwt:` block reading `JWT_SECRET` and `JWT_EXPIRY_MINUTES` |
| `.env.example`, `docker-compose.yml` | Changed | Pass the JWT settings through |

### Why

Before this, the platform had no concept of "who is calling". A token is the thing that lets
three separate services agree on an identity without any of them sharing a session store or
phoning user-service on every request.

### Decisions worth remembering

**Login lives in user-service, not a separate auth-service.** A separate service was built first
and then removed: it needed a fourth database created by hand, and split registration into two
writes across two databases with no shared transaction — requiring compensating deletes. Real
problems, worth learning, but a detour from understanding tokens.

**The `DEFAULT 'CUSTOMER'` in the migration is load-bearing.** The `customers` table already had
rows. A `NOT NULL` column cannot be added to a populated table without saying what goes in the
existing rows.

**Login failures are indistinguishable.** Unknown email, wrong password, and deactivated account
all return the same 401 with the same message. Different messages would let anyone discover
which addresses are registered — an attack called **user enumeration**.

**No default for `jwt.secret`.** A fallback value is how a project ships with a signing key that
anyone reading the repository already knows. Missing secret means the service refuses to start.

### Result

- `POST /api/v1/auth/login` returns `{ accessToken, tokenType: "Bearer", expiresIn: 900 }`
- The token carries `sub` (customer id), `role`, `iss`, `jti`, `iat`, `exp`
- Existing customers all became `CUSTOMER` when V3 ran; no data was lost
- Nothing checked the token yet — that was Step 2

---

## Phase 1, Step 3 — order-service

**Date:** Day 2

### What changed

New module `order-service` (port 8083), new database `ecommerce_order_db`, two migrations
(`orders`, `order_items`). Added to the root `pom.xml` and `docker-compose.yml`.

Key files: `entity/OrderStatus.java` (the state machine), `entity/Order.java` (aggregate root),
`entity/ShippingAddress.java` (embedded snapshot), `client/UserServiceClient.java` and
`client/ProductServiceClient.java` (OpenFeign), `client/ServiceErrorDecoder.java`,
`service/OrderService.java`.

### Why

An order needs customer and product data owned by other services. This is the service where the
project stops being three apps and becomes a distributed system.

### Decisions worth remembering

- **Prices never come from the client.** `OrderItemRequest` has only `productId` and `quantity`.
  A price field would let anyone buy anything for a cent.
- **One batch call for the whole cart**, not one per line. Otherwise it is the N+1 problem
  moved onto the network.
- **Orders snapshot what they reference** — unit price, product name, SKU, shipping address —
  so a past order never changes when the catalogue does.
- **`createOrder` is deliberately not `@Transactional`.** Annotating it would hold a database
  connection open across three HTTP calls; under load a slow dependency becomes a database outage.
- **Timeouts on every Feign client** (2s connect, 3s read). Without them, one hung dependency
  exhausts this service's threads and takes it down.
- **Downstream errors are translated**: a downstream 404 becomes our 422 naming what was
  missing; a timeout or 5xx becomes 503, never 500.

### Result

Placing an order calls all three services and computes the total from catalogue prices.
Reading an order needs no downstream calls at all — proven by an integration test.

---

## Phase 1, Step 2 — product-service

**Date:** Day 2

### What changed

New module `product-service` (port 8082), database `ecommerce_product_db`, migrations for
`categories` and `products`.

### Decisions worth remembering

- **Specifications for search.** Five optional filters means 32 possible combinations; derived
  query method names cannot cover that. `ProductSpecifications` builds the WHERE clause at runtime.
- **`/products/lookup` batch endpoint**, so order-service prices a whole cart in one call.
- **Money is `BigDecimal` / `NUMERIC(19,2)`**, never `double`. Floating point cannot represent
  `0.1` exactly.
- **SKU is a unique constraint, not the primary key**, and cannot be updated. Business
  identifiers change; primary keys should not.
- **422 introduced** for "well-formed request, unusable reference" — e.g. creating a product in
  a deactivated category.

---

## Phase 1, Step 1 — user-service and foundations

**Date:** Day 1

### What changed

Repository skeleton, aggregator `pom.xml`, `docker-compose.yml` with PostgreSQL, the init
script creating three databases and three logins, and the complete `user-service`.

### Decisions worth remembering

- **Database per service**, enforced by separate logins with `REVOKE ALL ... FROM PUBLIC`.
- **No cross-service foreign keys.**
- **DTOs everywhere**; entities are never serialised.
- **Flyway owns the schema**, Hibernate only validates it.
- **Soft delete** for customers, because orders reference them.
- **Optimistic locking** via `@Version`; concurrent updates return 409.
- **RFC 9457 Problem Details** for every error.
- **No shared `common` module** between services — that is the fastest route to a distributed
  monolith.
- **Testcontainers with real PostgreSQL**, not H2.

### Environment issues solved along the way

| Problem | Cause | Fix |
|---|---|---|
| Port 5432 already in use | A Windows PostgreSQL service was running | Stopped `postgresql-x64-18`; set its startup to Manual |
| `password authentication failed` | The init script runs once; editing `.env` afterwards does not change existing roles | `ALTER ROLE <name> WITH PASSWORD '<value>'` |
| `${USER_DB_USERNAME}` sent literally as the username | VS Code had the wrong folder open, so `${workspaceFolder}/.env` did not resolve | Reopened the inner `ecommerce-platform` folder |
| `verify-phase1.ps1` reported healthy services as down | Actuator's content type made PowerShell return bytes, not a string | Decode bytes before parsing; trust the status code |
| `HttpResponseException` type not found | That type only exists in PowerShell 7; this is Windows PowerShell 5.1 | Untyped `catch` |
