# order-service walkthrough

This is the service where microservices stop being "three Spring Boot apps" and start being a distributed system. It is the only service that calls other services, and every hard problem in Phase 1 lives here.

Same architecture as the other two, so this document covers only what is new.

---

## 1. OpenFeign: calling another service

**Why we need it.** Order-service cannot price a cart or validate a customer from its own database. That data belongs to other services.

**What problem it solves.** Writing HTTP clients by hand means URL building, serialisation, status-code checking and error handling repeated in every call site.

**How it works.** You declare an *interface*; Feign generates the implementation at startup.

```java
@FeignClient(name = "user-service", url = "${services.user.url}")
public interface UserServiceClient {
    @GetMapping("/api/v1/customers/{customerId}")
    CustomerDto getCustomer(@PathVariable UUID customerId);
}
```

Calling `getCustomer(id)` issues `GET http://localhost:8081/api/v1/customers/{id}` and deserialises the JSON into `CustomerDto`. `@EnableFeignClients` on the main class is what triggers the scan.

**Why this approach.** The alternative (`RestClient`/`WebClient`) is more explicit but noisier. Feign's real advantage here is testability: because the dependency is an *interface*, unit tests mock it and can simulate every downstream outcome — missing, inactive, timeout — with no network involved.

**Two configuration details that matter more than they look:**

`url` comes from configuration, not code. Locally that resolves to `localhost:8081`; inside Docker Compose, to `http://user-service:8081`. Same jar, different environment.

`FeignClientConfig` is **not** annotated `@Configuration`. Feign config classes are referenced from `@FeignClient(configuration = ...)` and instantiated per client. Marking it `@Configuration` would also register those beans globally — a genuinely common and confusing bug.

**Interviewer questions.**
- Feign vs RestTemplate vs WebClient vs the newer `RestClient`?
- How would you add service discovery later? (Drop `url`, add Eureka or Consul; `name` then resolves through the registry.)
- What does `@EnableFeignClients` actually do at startup?

---

## 2. Timeouts — the single most important line in the config

```yaml
spring.cloud.openfeign.client.config.default:
  connectTimeout: 2000
  readTimeout: 3000
```

**Why this is not optional.** Without a read timeout, a call to a hung service waits *forever*. That thread is never returned to the pool. Requests arrive, more threads block, and within seconds order-service has no threads left — it is down. Not because it broke, but because something it called got slow.

This is how one struggling service takes an entire platform down, and it is the most common real-world microservices outage. A timeout converts "hangs forever" into "fails in 3 seconds," which is survivable.

**Interviewer questions.**
- What happens to a thread pool when a downstream dependency hangs?
- What is a circuit breaker, and how does it differ from a timeout? (Timeout bounds one call; a circuit breaker *stops calling* after repeated failures, so you fail instantly instead of waiting 3 seconds every time. Resilience4j is the next step here.)
- What is a bulkhead? (Isolating thread pools per dependency so one slow dependency can't consume all capacity.)

---

## 3. Error translation at the boundary

**The problem.** A 404 from user-service, left alone, propagates as a raw `FeignException` and becomes a **500** from order-service. That is a lie: it tells the caller "I am broken" when the truth is "you referenced a customer that does not exist."

**How it works.** `ServiceErrorDecoder` intercepts every non-2xx downstream response and converts it into one of our own exceptions:

| Downstream | Our exception | Our HTTP response |
|---|---|---|
| 404 | `DownstreamNotFoundException` | → caught in the service → **422**, naming what was missing |
| 5xx | `DownstreamServiceException` | **503** Service Unavailable |
| timeout | Feign's `RetryableException` | **503** |
| other 4xx | default | 500 — we sent a bad request; that *is* our bug |

**Why 503 and not 500.** 500 means "this service is broken." 503 means "this service is fine but temporarily can't complete the request — retry shortly." That distinction drives real behaviour: load balancers and clients retry a 503, and on-call pages a 500.

**Callers of order-service never see Feign types.** That is the point of translating at the boundary.

**Interviewer questions.**
- Walk me through what happens end-to-end when product-service returns a 500.
- Why 503 rather than 500 for a downstream failure?
- Where should error translation live, and why not in every call site?

---

## 4. The create-order sequence — the order of operations *is* the design

```
1. GET customer        → must exist and be ACTIVE          (else 422)
2. GET address         → must exist AND belong to them     (else 422)
3. GET products/lookup → ONE batch call for the whole cart (else 422)
4. Build the order in memory, pricing from the catalogue
5. save() — one short local transaction
```

**Prices are never accepted from the client.** `OrderItemRequest` has only `productId` and `quantity`. If a price field existed, anyone could buy anything for a cent. The server of record for price is product-service, full stop.

**One batch call, not one per item.** A 10-item cart is one HTTP round trip. Calling a per-product endpoint in a loop is the N+1 problem moved onto the network — and it is *why* product-service has a `/lookup` endpoint at all.

**`createOrder` is deliberately not `@Transactional`.** A transaction begins the moment an annotated method is entered. Annotating this method would hold a database connection open across three HTTP calls. Under load that drains the connection pool, and a slow downstream service becomes a *database* outage. Instead, all remote work happens first, and the single `orderRepository.save(order)` at the end is itself transactional (Spring Data's repository implementation is annotated), persisting the order and its cascaded items in one short transaction.

**Ownership is enforced by URL shape.** Fetching the address via `/customers/{customerId}/addresses/{addressId}` means user-service performs the ownership check. A 404 covers both "no such address" and "not this customer's address" — and both are correctly our 422.

**Interviewer questions.**
- Why must the price come from the catalogue rather than the request?
- Why do the remote calls happen before the transaction opens?
- What happens if the product's price changes one second after step 3? (The order stands at the captured price. That is the accepted consistency model, and it's why the price is copied onto the line.)

---

## 5. Snapshots: why an order copies data instead of referencing it

`OrderItem` stores `productId` **and** `productSku`, `productName`, `unitPrice`. `Order` embeds the whole shipping address.

**Why.** An order is a historical record of a transaction. If the catalogue raises the price next week, or the product is discontinued, or the customer edits that address, **the order must not change**. A receipt that rewrites itself is not a receipt.

**What this buys you, concretely:**
- `GET /orders/{id}` works with product-service and user-service **switched off** — there's an integration test asserting no downstream calls happen on a read
- user-service can hard-delete addresses safely (which it does)
- No cross-service join is ever needed to render an order

This is deliberate denormalisation. In a monolith you would join to `products` and get today's price — which would be *wrong* for a historical order.

**Interviewer questions.**
- When is denormalisation correct rather than a smell?
- What is the difference between a reference and a snapshot, and how do you decide?
- How would you show the *current* product alongside the historical line? (Store the id — which we do — and fetch on demand, clearly labelled as current, not as what was charged.)

---

## 6. The order state machine

The legal transitions live in the `OrderStatus` enum, not in if-statements in the service:

```
PENDING → CONFIRMED → PAYMENT_PENDING → PAID → PROCESSING → SHIPPED → DELIVERED
   └────────────┴──────────────┘ → CANCELLED
```

**Why in the enum.** One place to read, one place to change, and it can be tested exhaustively without Spring, a database or mocks. `OrderStatusTest` does exactly that, including "no status can transition to itself" across every value.

**Cancelling after PAID is not allowed.** Money has changed hands; that needs a refund flow, not a silent status flip. Modelling this as a 409 now is more honest than pretending a state change is sufficient.

**The domain throws `IllegalStateException`; the service translates it** into `InvalidOrderStateException` → 409. The entity speaks domain language and knows nothing about HTTP — that separation is worth keeping.

**Cancel is idempotent**: cancelling an already-cancelled order returns it unchanged rather than failing, so a retried request behaves sensibly.

**Interviewer questions.**
- Why model this as a state machine rather than a status field anyone can set?
- Why is `PATCH /orders/{id}/cancel` a PATCH and not a PUT? (It's a command — a state transition — not a replacement of the resource.)
- How would you handle cancellation after payment properly?

---

## 7. Order as a true aggregate (the one `@OneToMany` in the platform)

user-service deliberately avoided `Customer.addresses`. Order-service deliberately *has* `Order.items`. The difference is real, not inconsistency:

An order item has no meaning outside its order, is always saved and deleted with it, and the order's total is only correct when computed across all items. That is what "aggregate" means — and it justifies `cascade = ALL, orphanRemoval = true`.

**Two related details:**

`getItems()` returns an **unmodifiable** list. Callers read items; they don't reach in and mutate the aggregate's internals. The total is recalculated inside `addItem`, so it can never drift from the lines.

`findWithItemsById` uses `@EntityGraph` to fetch items in the same query — otherwise rendering an order's lines is an N+1. But the **paged** customer-orders query deliberately does *not*, because combining a collection fetch-join with pagination makes Hibernate load every matching row into memory and paginate there. That's why the list view uses `OrderSummaryResponse`, which has no items field at all — the DTO enforces the contract the query relies on.

**Interviewer questions.**
- What is an aggregate root, and how do you decide the boundary?
- Why is `cascade = ALL` dangerous in general but correct here?
- What happens when you fetch-join a collection *and* paginate?

---

## 8. What is deliberately missing (and worth saying out loud)

These are known gaps, not oversights. Naming them is more impressive than pretending they don't exist:

- **No idempotency key on `POST /orders`.** Retrying a create could produce duplicate orders, so there are no automatic retries on it. The fix is an `Idempotency-Key` header stored with the order, returning the original result on replay.
- **No circuit breaker.** Timeouts bound each call, but repeated failures still cost 3 seconds each. Resilience4j is the next step.
- **No inventory or payment.** An order can be placed for an out-of-stock item. Those are separate services, and coordinating them is where the Saga pattern enters.
- **No distributed transaction, by choice.** Order-service commits only its own data. If the product is deactivated a second after validation, the order stands. That's the eventual-consistency trade-off, and the later Kafka phase is what makes such changes propagate.

---

## 9. Tests

| Test | Type | What it proves |
|---|---|---|
| `OrderStatusTest` | Pure unit | Every legal and illegal transition, exhaustively |
| `OrderServiceTest` | Unit, both Feign clients mocked | Pricing from the catalogue, snapshotting, 422 for every bad-reference case, mixed currencies, downstream outage propagation, duplicate-id de-duplication |
| `OrderControllerTest` | `@WebMvcTest` | Status codes including 422 and 503, cascaded validation into cart items, bad enum → 400 |
| `OrderRepositoryIntegrationTest` | `@DataJpaTest` + Testcontainers | Cascade save, `@EntityGraph` fetch, the same-product-twice unique constraint |
| `OrderApiIntegrationTest` | `@SpringBootTest` + Testcontainers, Feign mocked | Full lifecycle; reads need no downstream calls; a 422 persists nothing |

**The honest gap:** mocking the Feign clients means these tests do not prove our interfaces match the real services' contracts. If user-service renames a JSON field, every test here still passes and production breaks. **WireMock** (stub real HTTP) or **Spring Cloud Contract** (generate tests from a shared contract) closes that gap, and being able to say so is exactly the kind of answer that lands well in an interview.
