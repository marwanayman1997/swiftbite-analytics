# swiftbite-analytics

The analytics-service microservice: day-grained rollups of order/payment/delivery activity — per-restaurant, per-branch, per-product, and platform-wide — served read-only over HTTP. It owns no operational data (users, restaurants, products, orders, payments all live elsewhere) and emits no events of its own; it is a pure consumer and a pure read API.

Sibling repo `swiftbite-orders` is the upstream source of truth here: it publishes order/payment/delivery lifecycle events onto a RabbitMQ exchange via its own transactional outbox (mirroring `swiftbite-core`'s outbox pattern), and this service consumes them to build its aggregates. It never calls `swiftbite-orders` or `swiftbite-core` synchronously — everything it needs to serve a read comes from its own MongoDB.

Unlike its Node/TypeScript siblings, this service is **Java 21 + Spring Boot**. Where the ecosystems allow, it mirrors the same architectural conventions (the `app/lib/pkg` layering rule, the response envelope, the JWT contract, the transactional-outbox-and-dedupe pattern) rather than inventing new ones — see `CLAUDE.md` for the full convention-by-convention mapping and where it deliberately deviates.

## Stack

- Java 21, Spring Boot 3.5, Maven (`./mvnw` wrapper checked in — no global Maven install required)
- `spring-boot-starter-web`
- MongoDB (`spring-boot-starter-data-mongodb`, `MongoTemplate` — not Spring Data repositories, so the atomic `$inc` upserts stay explicit)
- Redis (`spring-boot-starter-data-redis` / Lettuce) — event-dedupe only, no general caching yet
- RabbitMQ (`spring-boot-starter-amqp`) — consumes `swiftbite-orders`' `order.events` exchange, manual ack, dead-letter routing
- `jjwt` — verifies the same JWT the other two services issue (shared `ACCESS_SECRET`, HS256); this service never issues its own
- SLF4J + Logback + `logstash-logback-encoder` (structured JSON logs, correlation id via MDC)
- ArchUnit (test scope) — compiles the `pkg → lib → app` layering rule into a failing test instead of relying on review

## Getting started

1. Install a JDK 21 if you don't have one (`brew install openjdk@21` on macOS; make sure it's first on `PATH`/`JAVA_HOME`, not just installed).

2. Start the backing infra (MongoDB, Redis, RabbitMQ) — this repo doesn't ship a `docker-compose.yml` yet; point at whatever local instances `swiftbite-core`/`swiftbite-orders` already use, or run your own:

   ```bash
   docker run -d --name mongo -p 27017:27017 mongo:7
   # Redis and RabbitMQ are already required by swiftbite-orders — reuse those.
   ```

3. Export the required environment variables (fail-fast at boot if missing — there are no insecure defaults for secrets):

   ```bash
   export ACCESS_SECRET=<same value as swiftbite-core / swiftbite-orders' ACCESS_SECRET>
   ```

   Everything else (`MONGODB_URI`, `REDIS_HOST`, `RABBITMQ_HOST`, `PORT`, ...) has a sensible `localhost` default in `src/main/resources/application.yml` — override only what your setup needs.

4. Run the tests (spins up real connections to Mongo/Redis/RabbitMQ — no mocks; see `CLAUDE.md`'s testing note about isolating RabbitMQ topology names):

   ```bash
   ./mvnw test
   ```

5. Start the server:

   ```bash
   ./mvnw spring-boot:run
   # or: ./mvnw -DskipTests package && java -jar target/analytics-service-0.1.0.jar
   ```

`swiftbite-orders`' outbox worker (in-process, started from its own `server.ts`) needs to be running and actually publishing to `order.events` for this service to have anything to consume — see that repo's README.

## Scripts

- `./mvnw spring-boot:run` — start the server with Spring Boot's dev tooling
- `./mvnw test` — run the full test suite (unit + real-infra integration tests)
- `./mvnw -DskipTests package` — build `target/analytics-service-0.1.0.jar`
- `java -jar target/analytics-service-0.1.0.jar` — run the packaged jar directly

## Response format

Same envelope as `swiftbite-core`/`swiftbite-orders`, built by `ApiResponse` (`src/main/java/.../lib/http/ApiResponse.java`):

```json
{ "success": true, "data": {}, "meta": {} }
```

`meta` is omitted unless present (pagination info). Errors (via `GlobalExceptionHandler`) return `{ "error": "message", "details": {} }` with the corresponding HTTP status — `details` is omitted unless present, and never wrapped in the success envelope.

## Authentication & authorization

Every non-`/actuator` route requires a valid `access_token` cookie (`JwtAuthFilter`) — same JWT contract as the other two services (`userId`, `role`, `restaurantId?`, `restaurantRole?`, `branchIds?`), verified with the same shared secret. Missing/invalid tokens return `401`.

There's no resource:action permission catalog here (unlike `swiftbite-core`'s RBAC) — every endpoint is a pure ownership check against the JWT's own claims, so this service never calls out to `swiftbite-core` synchronously:

- **Restaurant-day**: `system_admin` reads any restaurant; a `restaurant_user` may only read their own `restaurantId`.
- **Branch-day / product-day**: `system_admin` reads any branch; a `restaurant_user` needs the branch in their JWT's `branchIds`, *or* to hold `restaurantRole: "owner"` — in the owner case, the check additionally verifies the fetched data's `restaurantId` against the actor's own once any data exists, closing the "any owner reads any branch" gap that a JWT-claims-only check would otherwise leave open (see `BranchDayService`'s javadoc).
- **Platform-day**: `system_admin` only.

## Pagination & filtering

Every endpoint below accepts:

- `limit` — max rows per page (default `30`, capped at `100`)
- `cursor` — the previous page's `meta.nextCursor`; omit for the first page
- `from` / `to` — inclusive `yyyy-MM-dd` date bounds

Response shape: `{ "success": true, "data": [...], "meta": { "nextCursor": "...", "hasMore": true, "count": 30 } }`. Keep requesting with the latest `nextCursor` until `hasMore` is `false`. Every aggregate collection holds exactly one document per (dimension, date), so unlike the Knex-backed services' cursor implementation, no id tiebreaker is needed — the date alone is already a unique, strictly ordering key once a dimension is fixed. See `CursorPagination`.

## Event consumption

This service owns no outbox of its own — see `swiftbite-orders`' README for the producer side. What happens on this end:

1. `OrderEventsTopologyConfig` declares (idempotently, on every broker reconnect) the consumer queue `analytics-service.order-events`, bound to `swiftbite-orders`' `order.events` topic exchange on `order.#`, `payment.#`, `delivery.#`, with a dead-letter exchange routing to `analytics-service.order-events.dlq`.
2. `OrderEventsListener` consumes with manual ack: parses the envelope, checks Redis (`order-events:dedupe:<eventId>`, 24h TTL) before dispatching — **load-bearing** here, not just de-noising, since the Mongo upserts underneath use `$inc`, which double-counts on replay (unlike `swiftbite-orders`' own cache-invalidation consumer, where replay is harmless). A handler exception nacks to the DLQ; an unrecognized `eventType` just acks (not an error).
3. Each `eventType` is handled by an `OrderEventHandler` implementation registered as a Spring bean — `app/ingestion/service/{OrderPlacedIngestionHandler,OrderStatusChangedIngestionHandler}` — which fans the event out across whichever of the four aggregate collections it affects.

**Event types currently consumed:** `order.placed` (all four collections), `order.status_changed` for `cancelled`/`delivered` (restaurant/branch/platform-day only — every other status transition is a no-op here).

## API reference

Base URL: all routes below are mounted under `/api/v1/analytics`.

| Method | Path                                              | Auth                                    | Extras     |
| ------ | -------------------------------------------------- | ----------------------------------------- | ----------- |
| GET    | `/actuator/health`                                 | Public                                     |             |
| GET    | `/restaurants/:restaurantId/daily`                 | Auth required — own restaurant or admin    | Paginated   |
| GET    | `/branches/:branchId/daily`                        | Auth required — own branch/restaurant or admin | Paginated |
| GET    | `/branches/:branchId/products/:productId/daily`    | Auth required — own branch/restaurant or admin | Paginated |
| GET    | `/platform/daily?currency=`                        | system_admin only                          | Paginated   |

## Project structure

```
src/main/java/com/swiftbite/analytics/
  AnalyticsServiceApplication.java   # bootstrap
  app/
    restaurantday/    # document, repository, dto, service, controller — one restaurant's daily rollup
    branchday/         # same shape, one branch's daily rollup
    productday/          # same shape, one (product, branch)'s daily rollup
    platformday/           # same shape, platform-wide daily totals (keyed by date + currency)
    ingestion/                # fans one order-event out across the four modules above
      service/                  # OrderPlacedIngestionHandler, OrderStatusChangedIngestionHandler
  lib/
    auth/                # JwtAuthFilter, AuthenticatedUser
    config/               # AnalyticsProperties (env.ts equivalent — validated at boot)
    correlation/           # correlation-id filter (MDC-based, not passed by hand)
    error/                  # AppException + GlobalExceptionHandler
    events/                  # order.events envelope/handler interface/topology/listener (see Event consumption above)
    http/                     # ApiResponse envelope, CursorPagination
  pkg/                # framework/app-agnostic providers — none needed yet, see CLAUDE.md §3
  resources/
    application.yml       # env-backed config, localhost defaults
    logback-spring.xml      # structured JSON logging
```

Each `app/<module>day/` package follows the same layered shape: `document/` (Mongo document, no business logic beyond its `key()` factory) → `repository/` (`MongoTemplate`, explicit atomic-upsert methods — see `CLAUDE.md` §7 for the `$inc`/`$setOnInsert` split every one of them follows) → `dto/` (response shape — money as integer minor units + `currency`, dates as ISO strings, never a raw document) → `service/` (ownership check + repository call + DTO mapping) → `controller/` (validate → service → `ApiResponse`, no business logic). `lib/` never imports a concrete `app/` class — where it needs app-level behavior (event dispatch), it defines an interface (`OrderEventHandler`) that `app/` implements as a Spring bean, wired in by the DI container; `LayeringTest` (ArchUnit) enforces this as a compiled check. See `CLAUDE.md` for the full rationale behind every convention above.
