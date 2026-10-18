# Dukkan backend

Spring Boot 4.1 / Java 25 modular monolith implementing `packages/shared-contracts/openapi.yaml`
(business rules in `packages/shared-contracts/README.md`). Servlet stack, Spring Data JPA, PostgreSQL 18,
Flyway, stateless JWT auth. Versions and deviations: [VERSIONS.md](VERSIONS.md).

## Run locally

Requires JDK 25 (Gradle toolchain) and Docker.

```bash
# from the repository root: start PostgreSQL (db/user/password: dukkan, port 5432)
docker compose -f infrastructure/docker/compose.yaml up -d postgres

# from services/backend
./gradlew bootRun --args='--spring.profiles.active=local'
```

- API: `http://localhost:8080/api/v1` (e.g. `curl localhost:8080/api/v1/products?q=pasta`)
- Swagger UI: `http://localhost:8080/swagger-ui.html`, OpenAPI JSON: `/v3/api-docs`
- Health: `/actuator/health` (also `/actuator/health/liveness`, `/readiness`), info: `/actuator/info`

Flyway creates the schema (`V1__schema.sql`) and loads invented seed data (`V2__seed_data.sql`: 9 categories,
42 products, 6 promotions of which 5 are active until 2030, 3 hubs, 10 postal codes such as 41460, 40545, 50667).

### Configuration

| Setting | Env var | Default |
|---|---|---|
| `spring.datasource.url` | `DUKKAN_DB_URL` | `jdbc:postgresql://localhost:5432/dukkan` |
| `spring.datasource.username` / `password` | `DUKKAN_DB_USER` / `DUKKAN_DB_PASSWORD` | `dukkan` / `dukkan` |
| `dukkan.security.jwt.secret` | `DUKKAN_JWT_SECRET` | none; only the `local` profile has a dev default. Startup fails if missing or shorter than 32 bytes. |
| `dukkan.security.jwt.ttl` | `DUKKAN_JWT_TTL` | `PT1H` |
| `dukkan.checkout.minimum-order-cents` | | `3500` |
| `dukkan.delivery.*` | | capacity 8, cutoff hour 22, 7 days ahead, slots 08–20 |

Profiles: `local` (host development, plain-text logs, dev JWT secret); `docker` (used by the container,
ECS JSON logs, secrets from env only). Without a profile logs are also ECS JSON.

### Container

```bash
docker build -t dukkan-backend .
# or, from the repository root, database + backend:
DUKKAN_JWT_SECRET=$(openssl rand -base64 48) docker compose -f infrastructure/docker/compose.yaml --profile app up --build
```

The image builds the jar with the Gradle wrapper (tests are not run inside the image build), runs on
`eclipse-temurin:25.0.4.1_1-jre-noble` as non-root uid 10001, and listens on 8080.

## Test

```bash
./gradlew build      # compile (Error Prone, -Werror), unit + integration + contract + architecture tests
./gradlew test --tests 'de.dukkan.promotion.*'
```

Integration tests start `postgres:18.6` via Testcontainers (Docker must be running) and use a fixed,
movable clock (`support.MutableClock`, Monday 2026-10-19 10:00 Europe/Berlin).

| Test | Covers |
|---|---|
| `PricingEngineTest`, `PromotionStackingTest` | PERCENT_OFF half-up rounding per unit, MULTI_BUY groups + remainder, no promo, zero quantity, totals, stacking |
| `SlotPlannerTest` | Mon–Sat, 08:00–20:00 hourly, 7 days, 22:00 previous-day cutoff, DST, fee tiers |
| `JwtTokenServiceTest`, `*ValidationTest` | token claims/expiry/signature, secret fail-fast, Bean Validation of DTOs |
| `CheckoutFlowIntegrationTest` | register → login → browse/search → cart → addresses → slots → reserve → order → list → cancel |
| `SecurityIntegrationTest` | 401 problem responses, expired/tampered tokens, login enumeration, ownership (404), request id |
| `BusinessRulesAndErrorsIntegrationTest` | problem+json format, validation `errors[]`, all 422 codes incl. `MINIMUM_ORDER_NOT_MET` |
| `SlotCapacityConcurrencyIntegrationTest` | 12 concurrent reservations on an 8-capacity slot → exactly 8 succeed |
| `ContractMappingTest` | every operation in `openapi.yaml` has a handler and no `/api/v1` handler is undocumented |
| `ArchitectureTest` | module boundaries (ArchUnit), no cycles, layer packages |

The contract path is passed by Gradle as system property `dukkan.contract.openapi`
(`../../packages/shared-contracts/openapi.yaml`).

Dependencies are locked (`gradle.lockfile`, `buildscript-gradle.lockfile`, `settings-gradle.lockfile`).
After changing `gradle/libs.versions.toml`, refresh with `./gradlew resolveAndLockAll --write-locks`.
`./gradlew compileJava -PerrorpronePatch` applies Error Prone's suggested fixes in place.

## Module layout

Package-by-module under `de.dukkan`; each module has `api` (controllers, DTO records), `application`
(service interfaces, implementations, read models), `domain` (entities, value objects, pure rules) and
`infrastructure` (Spring Data repositories).

| Module | Responsibility | Uses |
|---|---|---|
| `identity` | users, BCrypt passwords, registration/login, JWT issuing | — |
| `address` | user addresses, `delivery_area` (postal code → hub, serviceability) | — |
| `delivery` | hubs, on-demand slot generation, capacity under `SELECT … FOR UPDATE`, area check | address |
| `promotion` | active promotions, stacking, pure `PricingEngine` | — |
| `catalog` | categories, products, price history, search and suggestions | promotion |
| `cart` | cart (optimistic `@Version`), pricing, slot reservation | catalog, promotion, address, delivery |
| `order` | checkout with snapshots, idempotency key, order numbers, cancellation | cart, address, delivery |
| `shared` | money, problem details, error codes, security config, clock, request-id filter, config properties | — |

Rules (enforced by `ArchitectureTest`): another module is used only through its `application` package;
an `api` package may also reuse another module's response DTOs (`DeliverySlotResponse`, `AddressResponse`,
`PromotionBadge`); `domain` and `infrastructure` are module-private; modules have no cycles.

## Notable behaviour

- Slot capacity counts active reservations and placed orders (`delivery_slot.used_capacity`, CHECK
  `0..capacity`). Reserving locks the slot row; replacing a reservation releases the old slot; placing an
  order transfers the unit to the order; cancelling or clearing the cart releases it. Expired
  reservations (past cutoff) are released when the cart is next read.
- Cart writes force a version increment, so concurrent writes to the same cart return
  `409 CONCURRENT_MODIFICATION`.
- `POST /orders` with the same `Idempotency-Key` for the same user returns the original order (201).
- Order numbers: `DK-yyyyMMdd-NNNNNN` (Berlin date, global sequence `order_number_seq`).

## Known limitations / backlog

- No rate limiting on `/auth/*` (brute force protection), no refresh tokens or logout/revocation.
- Deleting an address that a cart has reserved keeps the slot capacity held until the cart is cleared or the
  reservation replaced; checkout then fails with `SLOT_NOT_RESERVED`.
- Order list loads the live slot per order (N+1); fine for MVP volumes.
- Slots are generated lazily when listed; there is no scheduled clean-up of past slots.
- Recipes exist in the schema only (no API).
- The springdoc document is generated from code and is not identical to the shared contract.
