# product-service walkthrough

Same architecture as user-service, so most of it should feel familiar. This document focuses on **what is new or different**, and why — that difference is where the learning is.

## What is identical to user-service (and why that matters)

Layered packages, records as DTOs, hand-written mappers, `@RestControllerAdvice` returning RFC 9457 Problem Details, Flyway + `ddl-auto: validate`, UUID keys, `@Version` optimistic locking, soft deletes, `open-in-view: false`, Actuator, springdoc, the multi-stage Dockerfile, and the four-layer test strategy.

That repetition is deliberate. A platform where every service is structured differently is a platform nobody can navigate. When you open order-service next, you should already know where everything lives.

**`PageResponse` is duplicated**, not shared. It would be tempting to extract a `common` module — resist it. The moment two services share a released artifact of domain or API classes, they must be released together, and you have a distributed monolith. Small duplication is the cheaper trade.

---

## 1. Dynamic search with Specifications (the main new concept)

**Why we need it.** Product search takes five optional filters: keyword, category, status, min price, max price. A client may send any combination, including none.

**What problem it solves.** Derived query methods (like user-service's `findAllByStatus`) encode the query in the method *name*. Five optional filters means 2⁵ = 32 possible combinations. You cannot write 32 method names. And a single query with `WHERE (:status IS NULL OR status = :status)` repeated five times produces SQL the database can't plan or index well.

**How it works.** `ProductSpecifications.search(...)` builds a list of `Predicate` objects, adding one only if that filter was actually supplied, then ANDs them together. `ProductRepository` extends `JpaSpecificationExecutor<Product>`, which adds `findAll(Specification, Pageable)`. Hibernate turns the specification into exactly the WHERE clause needed — a request with only `?categoryId=X` produces a clean `WHERE category_id = ?`, nothing more.

The three arguments in the lambda are the Criteria API's building blocks:
- `root` — the entity being queried; `root.get("price")` refers to the `price` column
- `query` — the overall query object (unused here)
- `cb` (CriteriaBuilder) — the factory for conditions: `cb.equal`, `cb.like`, `cb.greaterThanOrEqualTo`

Keyword search uses `cb.like(cb.lower(name), "%term%")`. Honest limitation: **a leading-wildcard LIKE cannot use a normal B-tree index**, so it scans. That is fine for a catalogue of thousands; for millions you would move to PostgreSQL full-text search (`tsvector` + GIN index) or a dedicated search engine. Worth knowing that this is a deliberate "good enough for now" choice, not an oversight.

**Interviewer questions.**
- Specifications vs Querydsl vs `@Query` vs derived methods — when does each fit?
- Why can't `LIKE '%term%'` use an index, and what would you do at scale?
- What is the Criteria API, and why is it more verbose than JPQL?

---

## 2. The batch lookup endpoint (`GET /products/lookup?ids=a,b,c`)

**Why we need it.** Order-service must price a cart. A 10-item cart should be **one** HTTP call, not ten.

**What problem it solves.** The N+1 problem, but across the network. Ten sequential HTTP calls is ten round trips, ten chances to fail, and ten times the latency. This is one of the most common real microservice performance mistakes.

**How it works.** `findAllByIdIn(List<UUID>)` becomes a single `WHERE id IN (?, ?, ?)`. It's capped at 100 ids (`MAX_LOOKUP_IDS`), returning 422 beyond that, so one request cannot ask for the entire catalogue.

**Two design decisions worth noticing:**

**It returns a separate, smaller DTO** (`ProductLookupResponse`: id, sku, name, price, currency, status) rather than the full `ProductResponse`. This is a distinct contract for a distinct consumer. Order-service doesn't need descriptions or timestamps, and because it doesn't receive them, the catalogue's presentation fields can change without breaking order-service. An integration test explicitly asserts `description` is absent, to keep that boundary honest.

**Missing ids are silently absent** rather than throwing 404. The endpoint's job is "tell me about these products," and "this one doesn't exist" is a valid answer. Order-service compares what it asked for against what came back and decides how to report the gap — which it will, as a 422 naming the unknown product.

**Interviewer questions.**
- What's the N+1 problem at the service-call level, and how do you spot it?
- Why cap the batch size?
- Why a separate DTO for an internal consumer instead of reusing the public one?

---

## 3. Money: `BigDecimal` and `NUMERIC(19,2)`

**Why.** Prices must be exact.

**Problem.** `double` and `float` are binary floating point and cannot represent `0.1` exactly. Classic demonstration: `0.1 + 0.2` in Java prints `0.30000000000000004`. Accumulate that across an order and you get a total that's a cent off — which in a real business means failed reconciliation and angry finance teams.

**How.** `BigDecimal` in Java, `NUMERIC(19,2)` in PostgreSQL — both store decimal digits exactly. `@Digits(integer = 17, fraction = 2)` on the DTO rejects a request with more than two decimal places rather than silently rounding it. `@DecimalMin("0.00")` blocks negatives at the API, and `ck_products_price CHECK (price >= 0)` blocks them at the database too.

Currency is a separate ISO 4217 column. A price without a currency is meaningless, and order-service will reject a cart mixing currencies.

**Interviewer questions.**
- Why never use `double` for money?
- Why `compareTo` rather than `equals` on `BigDecimal`? (`equals` also compares scale, so `2.50` and `2.5` are "unequal".)
- Where else could you store money? (Integer minor units — cents — is the other common choice.)

---

## 4. Status code 422, and why it isn't 404 or 400

This service introduces a third failure category user-service didn't need.

| Code | Means | Example here |
|---|---|---|
| 400 | The request is malformed | `"price": "twenty"`, bad SKU format |
| 404 | The thing **in the URL** doesn't exist | `GET /products/{unknown-id}` |
| **422** | The request is well-formed, but something it **references** is unusable | Creating a product in a category that doesn't exist or is deactivated |
| 409 | Conflicts with existing state | Duplicate SKU, editing an inactive product |

**Why the distinction matters.** If creating a product with a bad `categoryId` returned 404, the client would reasonably think *the product* wasn't found — confusing for something being created. 422 says "I understood you; the content is the problem." This same pattern carries into order-service: unknown product in a cart → 422.

**Interviewer questions.**
- Walk me through 400 vs 404 vs 409 vs 422.
- Why does a category *id* problem become 422 but a category *format* problem become 400?

---

## 5. Business identifiers vs primary keys (SKU)

**Why it's interesting.** `sku` is unique, human-meaningful, printed on labels, used by warehouses. It's tempting to make it the primary key.

**Why we didn't.** Business identifiers change — companies re-scheme their SKUs, merge catalogues, fix typos. A primary key that changes means updating every foreign key and every external reference. So: UUID primary key (stable, internal, meaningless), SKU as a unique *constraint* (meaningful, business-owned).

Consequently **`UpdateProductRequest` has no SKU field**. Changing a SKU on an existing product breaks the link to physical inventory. Retire the product and create a new one instead. SKUs are normalised to uppercase and trimmed in the service, so uniqueness can't be defeated by case.

**Interviewer questions.**
- Natural vs surrogate keys: trade-offs?
- What breaks when a primary key changes?
- Why normalise before checking uniqueness? (Same reasoning as lower-casing email in user-service.)

---

## 6. Category as a lazy `@ManyToOne`, and a subtlety in the mapper

`Product.category` is `FetchType.LAZY`, unidirectional — same reasoning as `Address → Customer`.

But `ProductMapper.toResponse` reads `product.getCategory().getName()`. Reading `getId()` on a lazy proxy is free; reading **`getName()` forces a database query** to initialise the proxy. That works here only because mapping happens *inside* the service's transaction (the service returns DTOs, never entities) and `open-in-view: false` would have made it fail loudly otherwise.

This is exactly the kind of thing that makes `open-in-view: false` valuable: it turns "silent extra queries in production" into "obvious failure in development."

**Honest note:** search results map N products, each touching its category, which is an N+1 query. For a page of 20 it's negligible. The fix, if it mattered, is an `@EntityGraph` or a fetch join on the search query — worth being able to name in an interview.

**Interviewer questions.**
- What exactly is a lazy proxy, and when does it hit the database?
- What is `LazyInitializationException` and what causes it?
- How would you eliminate the N+1 in the search endpoint?

---

## 7. A small rule worth noticing: renaming to your own name

In `CategoryService.updateCategory`:

```java
if (!category.getName().equals(request.name()) && categoryRepository.existsByName(request.name())) {
    throw new DuplicateResourceException(...);
}
```

Without the first condition, saving a category without changing its name would fail as a "duplicate" — because it duplicates *itself*. Small bug, very common, and there's a unit test named for it.

---

## 8. Endpoint ordering: `/lookup` before `/{id}`

`GET /products/lookup` and `GET /products/{id}` both match the same shape. Spring MVC prefers the more specific literal path, so this works either way — but the methods are declared in that order, with a comment, so anyone reading top to bottom sees the intent rather than wondering whether "lookup" could be parsed as a UUID. There's a test asserting it.

---

## 9. Tests

Same four layers. New things tested:

| Test | Covers |
|---|---|
| `ProductServiceTest` | SKU normalisation, duplicate SKU rejected before the category is even fetched, missing/inactive category → 422, inverted price range, batch-lookup cap, partial lookup results |
| `CategoryServiceTest` | Duplicate names, the rename-to-own-name case, inactive categories read-only |
| `ProductControllerTest` | 201 + Location, every field error at once, 422 shape, `/lookup` routing |
| `ProductRepositoryIntegrationTest` | Real Flyway migrations, unique SKU, the negative-price CHECK constraint, and that the **Specification actually produces valid SQL** |
| `ProductApiIntegrationTest` | Full lifecycle; asserts the lookup projection doesn't leak `description` |

That Specification test matters more than it looks: a Specification is built at runtime, so a mistake in it (wrong property name, wrong join) compiles fine and only fails when executed. No mock can catch it.
