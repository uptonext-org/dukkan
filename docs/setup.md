# Setup guide

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Node.js | 24.22.0 (`.nvmrc`) | Mobile app, REA |
| JDK | Temurin 25.0.4 (LTS) | Backend. The Gradle toolchain asks for Java 25 |
| Docker + Compose | Recent Docker Engine | PostgreSQL 18.6 and the backend image. Testcontainers needs Docker for the backend tests |
| Android Studio + SDK / Xcode | As required by React Native 0.88 | Only to run the app on an emulator, simulator or device |
| kubectl | 1.36.5 | Optional. Only used to render the manifests |

Gradle needs no separate install: `services/backend/gradlew` downloads Gradle 9.8.1.

## 1. Database

```bash
cp .env.example .env                    # then set DUKKAN_JWT_SECRET (>= 32 bytes): openssl rand -base64 48
docker compose -f infrastructure/docker/compose.yaml up -d postgres
```

## 2. Backend

```bash
cd services/backend
./gradlew bootRun --args='--spring.profiles.active=local'    # http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html · health: /actuator/health
```
- The `local` profile has a development-only JWT secret and plain-text logs.
- On startup, Flyway creates the schema and loads the invented Hamburg seed data: 12 markets, 158 products in de/en/ar, promotions and banners.
- The `local` and `demo` profiles also create two demo accounts:

| Account | Password | Role |
|---|---|---|
| `kunde@dukkan.local` | `dukkan-demo-2026` | Customer, with a default address in St. Georg |
| `haendler@dukkan.local` | `dukkan-demo-2026` | Merchant who owns *Anatolia Frischmarkt* and *Backstube Sonnenkorn* |
| `admin@dukkan.local` | `dukkan-demo-2026` | Platform admin (`/api/v1/admin/**`: review, approve, suspend markets) |

Feature flags (`application.yml`, under `dukkan.features`):
- `require-login-to-browse`: default `false`. When `true`, browsing needs an account.
- `merchant-auto-approve`: default `true`. When `true`, new markets go live immediately.

**Uploaded images** (merchant photos, `POST /api/v1/merchant/markets/{id}/media`):

| Setting | Env var | Default |
|---|---|---|
| `dukkan.media.directory` | `DUKKAN_MEDIA_DIRECTORY` | `${java.io.tmpdir}/dukkan-media` (e.g. `/tmp/dukkan-media`); docker profile `/var/lib/dukkan/media` (Compose volume `media`) |
| `dukkan.media.public-base-url` | `DUKKAN_MEDIA_PUBLIC_BASE_URL` | empty: URLs are derived from the upload request (`http://<host>:8080/api/v1/media/<key>`) |
| `dukkan.media.max-file-size` / `spring.servlet.multipart.max-file-size` | | 5 MB (413 `FILE_TOO_LARGE`) |
| `dukkan.media.max-pixels` | | 40,000,000 (400 `IMAGE_TOO_LARGE`) |
| `dukkan.media.uploads-per-hour` | | 60 per user and instance (429 `RATE_LIMITED`) |

Image URLs are stored as absolute URLs. A photo uploaded from the Android emulator therefore points to
`http://10.0.2.2:8080/…`, which other devices cannot load; for shared test setups set
`DUKKAN_MEDIA_PUBLIC_BASE_URL` to an address every client reaches. Files in the default temp directory may be
removed by the OS; set `DUKKAN_MEDIA_DIRECTORY` to keep them.

**AI product capture** ("Schnell erfassen", `POST /api/v1/merchant/markets/{id}/product-drafts`, ADR 0011)
works without any key: the rule-based parser reads price, pack size and category from the text
(`engine: RULES`). To let Claude read photos and translate names, set an Anthropic API key:

| Setting | Env var | Default |
|---|---|---|
| `dukkan.capture.anthropic.api-key` | `DUKKAN_ANTHROPIC_API_KEY` | empty: rules only |
| `dukkan.capture.anthropic.model` | `DUKKAN_ANTHROPIC_MODEL` | `claude-opus-5-5` |
| `dukkan.capture.anthropic.effort` | | `low` |
| `dukkan.capture.anthropic.timeout` | | `PT30S` per attempt, one retry |
| `dukkan.capture.anthropic.server-side-fallbacks` | | `true` (`fallbacks: "default"`: a declined request is retried by the API on its recommended model) |
| `dukkan.capture.drafts-per-hour` | | 30 per merchant and instance (429 `RATE_LIMITED`) |

The key is read only from this setting (not from `ANTHROPIC_API_KEY`). With a key, merchant notes and
photos are sent to Anthropic; see ADR 0011 before enabling it in production.

To run the full stack in containers:
```bash
docker compose -f infrastructure/docker/compose.yaml --profile app up --build
```
The `docker` profile requires `DUKKAN_JWT_SECRET` and exits if it is missing
or too short. It logs ECS JSON.

## 3. Mobile app

```bash
cd apps/mobile
npm ci
npm start                 # Metro
npm run android           # or: npm run ios  (first: cd ios && bundle install && bundle exec pod install)
```
- **API base URL:** comes from `app.json` (`apiBaseUrl`). The defaults are `http://10.0.2.2:8080/api/v1` on the Android emulator and `http://localhost:8080/api/v1` on iOS.
- **Language:** the app starts in the device language (de/en/ar; fallback de). Switch it with the globe button. Arabic mirrors the layout.
- **First start:** asks for a location. Allow it, or search for e.g. "St. Georg", "20099" or "Ottensen".
- **Android maps:** Google Maps on Android needs `DUKKAN_MAPS_API_KEY` as a Gradle property or env var; it is never committed. iOS uses Apple Maps.

## 4. Quality checks

| Check | Command | Result on this branch (2026-10-21) |
|---|---|---|
| Backend compile + Error Prone + all tests | `cd services/backend && ./gradlew clean build` | Pass: 207 tests in 40 classes, 0 failures (incl. security-review regressions, admin, rate limit, slot clean-up, media upload) |
| Contract conformance | part of the backend build: `ContractMappingTest`, `ResponseSchemaIntegrationTest` | Pass: every operation is mapped in both directions; responses have the required fields and no undocumented ones |
| Mobile type check (strict) | `cd apps/mobile && npm run typecheck` | Pass, 0 errors |
| Mobile lint | `npm run lint` | Pass, 0 problems |
| Mobile tests | `npm test` | Pass: 20 suites, 88 tests (live test skipped). Jest prints a worker-exit warning (open handle not yet found) |
| Visual QA (web preview) | `cd tools/web-preview && npm ci && npm run build && npm run screenshots` (backend running) | 59 screenshots in de/ar, light/dark; reviewed after each design change. Known preview artefacts: Latin text alignment and start/end paddings in Arabic, map placeholder |
| **Live API test (app client ↔ real backend)** | backend running, then `DUKKAN_LIVE_API_URL=http://localhost:8080/api/v1 npx jest live.api` | Pass: 3 tests covering discovery in 3 languages, cart, slot, order, merchant accept, cancel, a new 3-language product found via Arabic search, and a photo upload (PNG generated in the test) set as a listing image and served by `GET /media/{key}` |
| Metro release bundles | `npx react-native bundle --platform android\|ios --dev false …` | Built |
| Contract lint | `npx -y @redocly/cli@2.64.1 lint packages/shared-contracts/openapi.yaml` | Valid, 0 errors (warnings only) |
| Mermaid diagrams | `@mermaid-js/mermaid-cli@11.12.0` on every block | All render |
| Kubernetes manifests | `kubectl kustomize infrastructure/kubernetes` | Renders |
| Mobile dependency audit | `npm audit --omit=dev --audit-level=critical` | 0 critical; 21 high, all from `braces <=3.0.3` via metro and the RN CLI (tooling, no fix published) |
| Native Android/iOS builds | none | **Not run.** No Android SDK or Xcode in the build sandbox (backlog B-28) |
| Backend dependency vulnerability scan | none | **Not run** (backlog B-29) |

CI (`.github/workflows/ci.yml`) runs:
- the contract lint;
- the backend build and image build;
- the mobile type check, lint, tests and audit;
- **`e2e-api`**, which starts the backend against a PostgreSQL 18.6 service and runs the live API test;
- the manifest render.

The audit step fails only on **critical** advisories.

## 5. Reverse-engineering tooling (REA)

REA is kept separate from the application and is not a project dependency.

```bash
npx rea-agents@6.7.0 setup --client claude_code --dry-run   # review the plan
npx rea-agents@6.7.0 setup --client claude_code --yes       # apply, then restart the agent
npx rea-agents@6.7.0 doctor --client claude_code
```
Android analysis also needs `REA_JADX_MCP_JAR`, pointing to a
`jadx-headless-mcp` 0.7.1 JAR, plus a full JDK. Put the artifacts you analyse
under `investigation/`, which is git-ignored. See
`docs/reverse-engineering/technical-analysis.md`.
