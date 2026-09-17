# E-commerce Platform

A microservices e-commerce backend built incrementally as a learning and portfolio project with Java 21, Spring Boot, PostgreSQL and Docker.

**Status: Phase 1 complete. Phase 2 (security) in progress.**

| Service | Status |
|---|---|
| user-service | Implemented |
| product-service | Implemented |
| order-service | Implemented |

user-service now issues JWTs at `POST /api/v1/auth/login`. Token *validation* in each service
is the next step — right now a token can be obtained but nothing yet checks it.

Kafka, Redis, an API gateway, observability, React and the data platform (Databricks, Spark,
Delta Lake) are planned for later phases and are intentionally not present yet.

## 1. Architecture

Three independently deployable Spring Boot services, each owning its own PostgreSQL database. Only order-service calls other services, synchronously over REST via OpenFeign. Inside each service the code is layered: `controller -> service -> repository`, with `dto`, `mapper`, `entity`, `exception` and `configuration` packages. Entities never leave the service layer.

Full design with diagrams: [docs/phase-1-design.md](docs/phase-1-design.md)
Component-by-component explanation of user-service: [docs/user-service-walkthrough.md](docs/user-service-walkthrough.md)
What is new and different in product-service: [docs/product-service-walkthrough.md](docs/product-service-walkthrough.md)
Cross-service communication, explained: [docs/order-service-walkthrough.md](docs/order-service-walkthrough.md)

## 2. Prerequisites

- JDK 21
- Maven 3.9+
- Docker with Docker Compose v2 (for PostgreSQL and for the integration tests)

## 3. Running PostgreSQL

```bash
cp .env.example .env        # then edit .env and replace every "change-me"
docker compose up -d postgres
docker compose ps           # wait for "healthy"
```

On first start with an empty volume, `infra/postgres/init/01-create-service-databases.sh` creates three databases and three logins, one per service. Each login can connect only to its own database.

The init script runs **only once**. If you change the database usernames or passwords in `.env` afterwards, recreate the volume (this deletes local data):

```bash
docker compose down -v && docker compose up -d postgres
```

## 4. Running the services

**Working in VS Code with pgAdmin?** See [docs/day-3-setup.md](docs/day-3-setup.md) for the
one-command daily startup, one-click service launches, debugging, and browsing the data in
pgAdmin. The manual steps below still work and are worth understanding once.


### user-service from your shell or IDE

Spring Boot reads configuration from environment variables. Export the values from `.env` into your shell, then start the service:

```bash
set -a; source .env; set +a
cd user-service
mvn spring-boot:run
```

In IntelliJ, add `USER_DB_USERNAME` and `USER_DB_PASSWORD` to the run configuration's environment variables instead.

Flyway creates the tables on first start. The service listens on http://localhost:8081.

### product-service from your shell or IDE

Same pattern, different variables and port. Run it in a **separate terminal** from user-service — both stay running at the same time.

```bash
set -a; source .env; set +a
cd product-service
mvn spring-boot:run
```

It listens on http://localhost:8082 and uses `ecommerce_product_db`.

On Windows PowerShell, set the two variables for the session before running:

```powershell
$env:PRODUCT_DB_USERNAME="product_service"
$env:PRODUCT_DB_PASSWORD="<the password from your .env>"
mvn -pl product-service spring-boot:run
```

### order-service from your shell or IDE

order-service calls the other two, so start user-service and product-service first.

```bash
set -a; source .env; set +a
cd order-service
mvn spring-boot:run
```

On Windows PowerShell:

```powershell
$env:ORDER_DB_USERNAME="order_service"
$env:ORDER_DB_PASSWORD="<the password from your .env>"
mvn -pl order-service spring-boot:run
```

It listens on http://localhost:8083. It finds the other services via `USER_SERVICE_URL` and
`PRODUCT_SERVICE_URL`, which default to localhost:8081 and localhost:8082.

### The services in Docker

```bash
docker compose --profile apps up --build
```

### Tests

```bash
cd user-service
mvn test       # unit, web-slice and Testcontainers integration tests (Docker must be running)
```

Or build everything from the repository root with `mvn verify`.

## 5. Verifying the whole platform

With all three services running, this script exercises every Phase 1 behaviour end to end and
prints what it found:

```powershell
.\scripts\verify-phase1.ps1
```

It creates its own uniquely-named test data and deletes nothing. It checks order placement
across all three services, that prices cannot be supplied by the client, that an order
snapshots what it references, 422 for bad references, the order state machine, and idempotent
cancellation.

To see graceful degradation, stop product-service (Ctrl+C in its window) and run:

```powershell
.\scripts\verify-degradation.ps1 -OrderId <an order id from the first script>
```

Writes fail fast with 503 while reads keep working.

## 6. API documentation

With user-service running:

| Service | Swagger UI | OpenAPI JSON | Health |
|---|---|---|---|
| user-service | http://localhost:8081/swagger-ui.html | /v3/api-docs | /actuator/health |
| product-service | http://localhost:8082/swagger-ui.html | /v3/api-docs | /actuator/health |
| order-service | http://localhost:8083/swagger-ui.html | /v3/api-docs | /actuator/health |

Quick smoke test:

```bash
curl -i -X POST http://localhost:8081/api/v1/customers \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Jane","lastName":"Doe","email":"jane.doe@example.com","phone":"+14165550123","password":"correct-horse-battery"}'

curl 'http://localhost:8081/api/v1/customers?page=0&size=10&sort=lastName,asc'
```

Product catalogue smoke test (create a category first, since a product needs one):

```bash
curl -i -X POST http://localhost:8082/api/v1/categories \
  -H 'Content-Type: application/json' \
  -d '{"name":"Apparel","description":"Clothing and accessories"}'

# Use the id from that response as categoryId below
curl -i -X POST http://localhost:8082/api/v1/products \
  -H 'Content-Type: application/json' \
  -d '{"sku":"TSHIRT-BLK-M","name":"Black cotton t-shirt (M)","price":24.99,"currency":"CAD","categoryId":"<category-id>"}'

curl 'http://localhost:8082/api/v1/products?q=shirt&status=ACTIVE&sort=price,asc'
```

Placing an order ties all three services together. You need a customer id and one of their
address ids from user-service, plus a product id from product-service:

```bash
curl -i -X POST http://localhost:8083/api/v1/orders \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"<customer-id>","shippingAddressId":"<address-id>","items":[{"productId":"<product-id>","quantity":2}]}'
```

Note what the request does *not* contain: any price. order-service fetches prices from
product-service and computes the total itself.

The full endpoint list for all three services is in [docs/phase-1-design.md#4-api-contract](docs/phase-1-design.md#4-api-contract).

## 7. Database structure

| Database | Tables | Notes |
|---|---|---|
| ecommerce_user_db | customers, addresses | FK addresses -> customers (same service) |
| ecommerce_product_db | categories, products | FK products -> categories (same service) |
| ecommerce_order_db | orders, order_items | `customer_id` and `product_id` are plain UUIDs with no FK: they belong to other services |

Conventions: UUID primary keys, `NUMERIC(19,2)` for money, `TIMESTAMPTZ` in UTC, enums stored as strings with `CHECK` constraints, a `version` column for optimistic locking. The schema is owned by Flyway; Hibernate only validates it.

## 8. Important design decisions

- **Database per service**, enforced with separate database logins, and no cross-service foreign keys.
- **Snapshots over joins**: orders will copy price and shipping address at order time.
- **DTOs everywhere**; entities are never serialised. Passwords are BCrypt-hashed and never returned or logged.
- **Soft delete** for customers (orders reference them); **hard delete** for addresses (orders keep a copy).
- **Optimistic locking** with `@Version`; concurrent updates return 409 rather than silently overwriting.
- **RFC 9457 Problem Details** for every error.
- **Flyway + `ddl-auto: validate`**: versioned, reviewable schema changes.
- **No shared code library** between services, to avoid coupling their release cycles.
- **Testcontainers with real PostgreSQL** instead of H2.
- **Specifications for dynamic search** in product-service, where five optional filters make derived query methods unworkable.
- **Batch lookup endpoint** (`/products/lookup`) so order-service prices a whole cart in one call, not one per item.
- **Business identifiers are not primary keys**: SKU is a unique constraint, the primary key stays a UUID, and SKU cannot be updated.
- **Prices are never accepted from the client**: order-service fetches them from product-service and computes the total itself.
- **Orders snapshot what they reference** (unit price, product name/SKU, shipping address), so a past order never changes when the catalogue or the customer's address does. Reading an order needs no downstream calls.
- **Timeouts on every Feign client**, and remote calls happen *before* the database transaction opens, so a slow dependency cannot drain the connection pool.
- **Downstream failures are translated**: a downstream 404 becomes a 422 naming what was missing; a timeout or 5xx becomes a 503, never a 500.
- **Order transitions live in a state machine** on the `OrderStatus` enum, not in scattered if-statements.
- **No secrets in Git**: credentials come from environment variables; `.env` is git-ignored.
- **Passwords are BCrypt-hashed**, never stored or returned in plain text, and never logged.
- **Login failures are indistinguishable**: an unknown email and a wrong password return the same 401, so the API cannot be used to discover which addresses are registered.
- **JWTs carry the customer id and role**, are signed with HS256, and expire in 15 minutes — a token cannot be revoked once issued, so short expiry is what bounds the damage.

## 9. How the services communicate

Clients call each service directly on its own port (8081, 8082, 8083). When an order is placed, order-service calls user-service to confirm the customer is active and to fetch the shipping address, and calls product-service once, in a batch, to get current prices. Remote calls have timeouts, happen before the local database transaction begins, and their failures are translated into 422 or 503 responses. There are no distributed transactions. Details and a sequence diagram: [docs/phase-1-design.md#5-service-communication-design](docs/phase-1-design.md#5-service-communication-design).

## Framework version note

This project uses Spring Boot 3.5.16, the final open-source release of the 3.x line. Spring Boot 3.x reached the end of open-source support on June 30, 2026; supported open-source lines are now 4.0.x and 4.1.x. Upgrading to 4.x is planned as a deliberate, documented step.
