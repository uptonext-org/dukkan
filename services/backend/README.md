# Dukkan backend

Spring Boot 4.1 / Java 25 modular monolith for the Dukkan **local-market marketplace** (Hamburg launch).
It implements `packages/shared-contracts/openapi.yaml` (v0.2); the business rules are in
`packages/shared-contracts/README.md` and the product decisions in `docs/product/marketplace.md`.
Servlet stack, Spring Data JPA + JdbcClient, PostgreSQL 18 (with `pg_trgm`), Flyway, stateless JWT auth.
Versions: [VERSIONS.md](VERSIONS.md). Schema: [`docs/architecture/database.md`](../../docs/architecture/database.md).

## Run locally

Requires JDK 25 (Gradle toolchain) and Docker.

```bash
# from the repository root: PostgreSQL (db/user/password: dukkan, port 5432)
docker compose -f infrastructure/docker/compose.yaml up -d postgres

# from services/backend; the local profile also creates the demo accounts
./gradlew bootRun --args='--spring.profiles.active=local'
```

- API: `http://localhost:8080/api/v1`, e.g.
  `curl 'localhost:8080/api/v1/markets?lat=53.5551&lng=10.0128'` (markets near St. Georg) or
  `curl -H 'Accept-Language: ar' 'localhost:8080/api/v1/search/products?q=apfel&lat=53.5551&lng=10.0128'`
- Swagger UI: `/swagger-ui.html`, OpenAPI JSON: `/v3/api-docs` (generated, for exploration; the shared contract is authoritative)
- Health: `/actuator/health` (`/liveness`, `/readiness`), info: `/actuator/info`

### Demo accounts (profiles `local` and `demo` only)

`DemoAccountsInitializer` creates them on startup (idempotent). They are **not** in the Flyway migrations.

| Email | Password | Roles | Notes |
|---|---|---|---|
| `kunde@dukkan.local` | `dukkan-demo-2026` | CUSTOMER | default address Pfefferminzgang 4, 20099 Hamburg (St. Georg) |
| `haendler@dukkan.local` | `dukkan-demo-2026` | CUSTOMER, MERCHANT | owns *Anatolia Frischmarkt* and *Backstube Sonnenkorn* |

The password is a development-only value. For the container use `--spring.profiles.active=docker,demo`.

### Data

Flyway: `V1__schema.sql` (schema, `pg_trgm`), `V2__reference_data.sql` (locales de/en/ar, UI texts, 9 market
types, 10 cuisines, 12 categories, Hamburg gazetteer: 22 districts, their postal codes and 28 streets),
`V3__seed_hamburg.sql` (12 invented markets across Hamburg districts, 158 shared catalog products with de/en/ar
names, 529 listings, 34 banners, 23 promotions), `V4__product_icon_keys.sql` (placeholder icon per product),
`V5__listing_image.sql` (`market_product.image_url`, a market's own photo of a listing). Timestamps in V3 are relative to `now()` (promotions valid for a
year, three "new" listings per market). Market names, street names (spice-themed, e.g. *Kardamomweg*), brands
and people are invented. The search columns of seeded products are built at startup by `SearchTextIndexer`.

### Configuration

| Setting | Env var | Default |
|---|---|---|
| `spring.datasource.url` | `DUKKAN_DB_URL` | `jdbc:postgresql://localhost:5432/dukkan` |
| `spring.datasource.username` / `password` | `DUKKAN_DB_USER` / `DUKKAN_DB_PASSWORD` | `dukkan` / `dukkan` |
| `dukkan.security.jwt.secret` | `DUKKAN_JWT_SECRET` | none; only `local` has a dev default. Startup fails if missing or < 32 bytes |
| `dukkan.security.jwt.ttl` | `DUKKAN_JWT_TTL` | `PT1H` |
| `dukkan.features.require-login-to-browse` | `DUKKAN_REQUIRE_LOGIN_TO_BROWSE` | `false` |
| `dukkan.features.merchant-auto-approve` | `DUKKAN_MERCHANT_AUTO_APPROVE` | `true` |
| `dukkan.fulfilment.*` | | 7 days ahead, `PT1H` slots, capacity 6 (delivery) / 10 (pickup), cutoff `PT2H` (delivery) / `PT1H` (pickup) |
| `dukkan.catalog.*` | | "new" = 14 days, default max order quantity 24 |
| `dukkan.markets.*` | | default radius 10 km, max 50 km |
| `dukkan.media.directory` | `DUKKAN_MEDIA_DIRECTORY` | `${java.io.tmpdir}/dukkan-media`; docker profile `/var/lib/dukkan/media` (volume) |
| `dukkan.media.public-base-url` | `DUKKAN_MEDIA_PUBLIC_BASE_URL` | empty = derived from the upload request (`<origin>/api/v1/media`) |
| `dukkan.media.max-file-size`, `max-pixels`, `uploads-per-hour` | | 5 MB, 40,000,000, 60 per user and instance. `spring.servlet.multipart.max-file-size` is 5 MB too (request 6 MB), `server.tomcat.max-swallow-size` 16 MB so oversized uploads still get their 413 |

**Feature flags**

- `require-login-to-browse`: when `true`, the catalog GETs (`/markets/**` reads, `/products/*`, `/search/products`,
  slots) answer 401 without a token. `/locales`, `/market-types`, `/cuisines`, `/geo/*` and `/auth/*` stay public.
- `merchant-auto-approve`: new merchant markets are `ACTIVE` immediately; when `false` they are `PENDING_REVIEW`
  (invisible to customers; there is no admin approval API yet).

Profiles: `local` (plain-text logs, dev JWT secret, demo accounts), `docker` (ECS JSON logs, secrets from env),
`demo` (demo accounts; combine with `docker`). Without a profile logs are ECS JSON.

### Container

```bash
docker build -t dukkan-backend .
DUKKAN_JWT_SECRET=$(openssl rand -base64 48) docker compose -f infrastructure/docker/compose.yaml --profile app up --build
```

The image builds the jar with the Gradle wrapper (tests are not run in the image build), runs on
`eclipse-temurin:25.0.4.1_1-jre-noble` as non-root uid 10001 and listens on 8080.

## Test

```bash
./gradlew clean build   # Error Prone (-Werror), unit, integration, contract and architecture tests
./gradlew test --tests 'de.dukkan.CrossLanguageSearchIntegrationTest'
```

Integration tests start `postgres:18.6` via Testcontainers (Docker must be running) with the full seed data. The
test clock (`support.MutableClock`) starts on **Monday 10:00 Europe/Berlin of the current week**, so weekday rules
are deterministic while seed timestamps relative to `now()` stay consistent. Test tokens live 30 days.

| Test | Covers |
|---|---|
| `PricingEngineTest`, `PromotionStackingTest` | PERCENT_OFF half-up rounding, MULTI_BUY groups, newest promotion wins, de-DE money format |
| `TextNormalizerTest`, `SearchTextTest` | German/Latin folding (ä→a, ß→ss, ç, ł), Arabic (tashkeel, tatweel, أ إ آ→ا, ى→ي, ة→ه, digits); search text, Grundpreis |
| `LocaleResolutionTest` | `Accept-Language` matching (`ar-EG`→ar, `fr,en`→en, unsupported→de), fallback requested → market primary → de → any |
| `OpeningScheduleTest` | openNow / todayHours / nextOpeningAt with fixed instants, lunch breaks, Sunday, DST, Berlin time |
| `SlotPlannerTest`, `DeliveryFeeTest`, `OrderStatusTest`, `GeoMathTest` | slot windows Mon–Sat in opening hours, cutoffs 2 h/1 h, DST; free delivery threshold; status machine; Haversine and bounding box |
| `MarketDiscoveryIntegrationTest` | radius search, filters (types, cuisines, fulfilment, openNow, hasOffers, q), all sorts, paging, details, suspended markets |
| `MarketHomeIntegrationTest` | banners, PROMOTIONS/NEW/CATEGORY sections, localisation, categories with counts, promotions |
| `CrossLanguageSearchIntegrationTest` | "تفاح", "apple", "apfel", "Äpfel", "elma" find the same listing; Arabic names + `Content-Language: ar`; ranking; suggestions; nearby price comparison; filters |
| `CustomerFlowIntegrationTest` | browse → carts in two markets → delivery + pickup → orders → merchant status changes → cancellation rules, idempotency |
| `BusinessRulesIntegrationTest` | problem+json, `errors[].code`, every 422 cart/slot/checkout code, delivery outside radius → 422 |
| `FavoritesIntegrationTest` | favourite markets/products, FREQUENTLY_ORDERED (≥ 2 non-cancelled orders), grouping, home section |
| `MerchantIntegrationTest` | market create/update/validation, listing from catalog (409 on duplicate), private product found in other languages, promotions, other merchant → 404, customer → 403 |
| `SlotCapacityConcurrencyIntegrationTest` | 16 concurrent reservations on a 10-capacity pickup slot → exactly 10 succeed |
| `RequireLoginToBrowseIntegrationTest` | flag on → 401 on catalog GETs; auto-approve off → PENDING_REVIEW |
| `ResponseSchemaIntegrationTest` | real responses contain every required property of ~35 contract schemas |
| `SecurityIntegrationTest`, `GeoIntegrationTest`, `MetaAndLocalizationIntegrationTest` | 401/403, tokens, roles, ownership, request id; gazetteer search/reverse/geocoding; locales, code lists, PATCH /me |
| `MediaIntegrationTest`, `ImageInspectorTest`, `UploadRateLimiterTest` | JPEG/PNG/WebP fixtures generated in the test; spoofed content types (HTML/SVG/GIF declared as images) → 415, truncated/garbage → `INVALID_IMAGE`, > 5 MB → 413, > 40 MP (header only) → `IMAGE_TOO_LARGE`; other merchant → 404, customer → 403; GET bytes + Cache-Control/ETag/nosniff, 304; dedupe; logo/cover/listing/private-product photos; 429 in `RateLimitIntegrationTest` |
| `ContractMappingTest`, `ArchitectureTest` | every contract operation has a handler and vice versa; module boundaries, no cycles, layers |

Dependencies are locked (`gradle.lockfile`, `buildscript-gradle.lockfile`, `settings-gradle.lockfile`). After
changing `gradle/libs.versions.toml`, run `./gradlew resolveAndLockAll --write-locks`.

## Modules

Package-by-module under `de.dukkan`; each module has `api` (controllers, request/response DTOs), `application`
(services, read models, ports), `domain` (entities, pure rules) and `infrastructure` (repositories, SQL).

| Module | Responsibility | Uses |
|---|---|---|
| `i18n` | `supported_locale`, `Accept-Language` resolution, `Content-Language` filter, `ContentLocale` fallback, `ui_text`, `TextNormalizer` | — |
| `geo` | Haversine/bounding box, `GeocodingService` + `GazetteerGeocodingService` (`gazetteer_place`) | i18n |
| `identity` | users, roles (JWT `roles` claim), BCrypt, register/login, `PATCH /me` | i18n |
| `promotion` | market promotions, stacking, localised badges, pure `PricingEngine` | i18n |
| `market` | markets, types, cuisines, opening hours (`OpeningSchedule`), banners, owners, radius search | i18n, geo, promotion |
| `catalog` | categories, shared catalog + translations, listings, multilingual search, market home composition | i18n, market, promotion |
| `address` | user addresses, geocoding fallback | geo |
| `fulfilment` | slot generation, capacity under `SELECT … FOR UPDATE`, delivery-area check, delivery-fee rule | market, address, geo |
| `cart` | one cart per user and market (optimistic `@Version`), pricing, reservation | catalog, market, fulfilment, address |
| `order` | checkout per market with snapshots, idempotency, status machine, merchant order views | cart, fulfilment, address, market, catalog, identity |
| `favorite` | favourite markets/listings, favourites page grouped by category | catalog, market |
| `merchant` | `/merchant/**` facade with ownership checks (also for uploads); demo accounts runner | market, catalog, promotion, order, identity, address, media |
| `media` | image validation (magic bytes, `javax.imageio`, WebP header), content-addressed `MediaStorage` (filesystem), per-user upload limit, public `GET /media/{key}` | — |
| `shared` | money, problems, error codes, security, properties, clock, request id | — |

Ports keep the graph acyclic: `catalog.application.ListingFavorites` (favourite flags) is implemented by
`favorite`, `catalog.application.PurchaseHistory` (frequently ordered) by `order`. The market home lives in
`catalog` because every section is a product list.

Rules (`ArchitectureTest`): another module is used only through its `application` package (API layers may reuse
other modules' response DTOs from `api`); `domain` and `infrastructure` are module-private; no module cycles;
`domain` does not depend on `api`, `infrastructure` or Spring Web.

## Notable behaviour

- **Locales**: content is stored in `*_translation` tables; fallback requested → market primary → `de` → any.
  `Content-Language` is the resolved locale (or `de`). Error `detail` is English; `errors[].code` is the
  constraint name (e.g. `NotBlank`). A new language needs only rows (`supported_locale`, translations, `ui_text`).
- **Search**: `catalog_product.search_text` = normalised names of all translations + brand + keywords, queried
  with `ILIKE %term%` on a `pg_trgm` GIN index. Ranking: prefix of the localised name, prefix of any
  translation, substring; then shorter names.
- **Radius**: `lat/lng` double columns with a (lat, lng) index; SQL bounding box prefilter + exact Haversine.
  `openNow` and the name filter are applied after SQL (the candidate set within ≤ 50 km is small), then paged.
- **Slots**: generated idempotently (`ON CONFLICT DO NOTHING`) when listed: today + 6 days, Mon–Sat, 1 h windows
  inside opening hours that start in the future. Slot fee = market delivery fee (pickup 0); the cart applies
  free delivery from `freeDeliveryFrom`. A reservation holds one unit until the cutoff, clearing or replacement;
  orders keep it; REJECTED and CANCELLED release it.
- **Orders**: `DK-yyyyMMdd-NNNNNN`; same `Idempotency-Key` for the same user returns the first order (201).
  Item names are re-localised from the catalog when shown (snapshot as fallback).

- **Media**: `POST /merchant/markets/{id}/media` → `merchant` checks ownership → `media.MediaService`:
  rate limit (fixed 1 h window per user), size, format by magic bytes (JPEG `FF D8 FF`, PNG signature,
  `RIFF….WEBP`), header dimensions and the 40 MP limit *before* decoding, then a full decode of JPEG/PNG with
  ImageIO subsampled to ~1 MP (bounded memory; decoder warnings such as a truncated JPEG reject the file).
  WebP is header-checked only (VP8/VP8L/VP8X), since the JDK has no WebP decoder. Files are stored unchanged
  under `sha256(bytes).ext`, sharded `ab/abcd….ext`, written atomically (temp file + rename); the same bytes
  dedupe to one file and one URL. The URL goes into the existing `logoUrl`, `coverImageUrl`, `newProduct.imageUrl`
  or the new `UpdateListingRequest.imageUrl`, so profile and listing APIs are unchanged. A listing photo of a
  shared catalog product is stored on `market_product.image_url` (overrides `catalog_product.image_url` for that
  market only); for a private product it is the product image.
- **Swapping storage**: `media.application.MediaStorage` has three methods (`exists`, `put`, `open`). An S3/MinIO
  implementation (AWS SDK v2 or MinIO client, `PutObject` with `If-None-Match: *`) replaces
  `infrastructure.FileSystemMediaStorage`; point `dukkan.media.public-base-url` at the bucket's CDN so that
  `GET /media/{key}` is no longer on the request path. No SDK is a dependency yet (backlog B-32a).

## Known limitations / backlog

- No admin API (approving `PENDING_REVIEW` markets, suspending); status changes need SQL.
- `/merchant/catalog` has no market parameter in the contract, so `alreadyListed` is only set when the merchant
  owns exactly one market (null otherwise).
- `Content-Language` reports the requested locale (or `de`) even if a field fell back to a market's primary locale.
- Opening intervals cannot span midnight. Slots are not cleaned up; changing opening hours does not delete
  already generated slots.
- Nearby search ranks at most 1000 matches in memory and sorts `price_asc` by the regular price.
- No refresh tokens; payment simulated. Rate limits (auth per IP, uploads per user) are per instance.
- Uploaded media: filesystem storage only (an `emptyDir` in Kubernetes, not shared between replicas); no
  thumbnails or resizing (the app downsizes to 2048 px before upload); EXIF metadata is kept as uploaded; files are
  never deleted (no reference counting); WebP pixel data is not decoded; CMYK JPEGs are accepted via the raster
  path, other exotic JPEG/PNG variants ImageIO cannot read are rejected as `INVALID_IMAGE`.
- The gazetteer covers Hamburg only (22 districts); reverse geocoding answers 404 beyond 25 km of a known place.
