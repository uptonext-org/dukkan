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
| `dukkan.media.storage` | `DUKKAN_MEDIA_STORAGE` | `filesystem`; `s3` for an S3-compatible bucket (needed for several instances) |
| `dukkan.media.s3.*` | `DUKKAN_MEDIA_S3_ENDPOINT`, `_REGION`, `_BUCKET`, `_ACCESS_KEY`, `_SECRET_KEY`, `_PATH_STYLE`, `_CREATE_BUCKET` | endpoint empty = AWS; keys empty = AWS default credentials chain |

To try S3 locally: `docker compose -f infrastructure/docker/compose.yaml --profile s3 up -d s3` (Adobe S3Mock on
port 9090), then start the backend with `DUKKAN_MEDIA_STORAGE=s3 DUKKAN_MEDIA_S3_ENDPOINT=http://localhost:9090
DUKKAN_MEDIA_S3_BUCKET=dukkan-media DUKKAN_MEDIA_S3_PATH_STYLE=true DUKKAN_MEDIA_S3_CREATE_BUCKET=true
DUKKAN_MEDIA_S3_ACCESS_KEY=dev DUKKAN_MEDIA_S3_SECRET_KEY=dev`.

Image URLs are stored as absolute URLs. A photo uploaded from the Android emulator therefore points to
`http://10.0.2.2:8080/…`, which other devices cannot load; for shared test setups set
`DUKKAN_MEDIA_PUBLIC_BASE_URL` to an address every client reaches. Files in the default temp directory may be
removed by the OS; set `DUKKAN_MEDIA_DIRECTORY` to keep them.

**AI product capture** ("Schnell erfassen", `POST /api/v1/merchant/markets/{id}/product-drafts`, ADR 0011)
works without any key: the rule-based parser reads price, pack size and category from the text
(`engine: RULES`). To let Claude read photos and translate names, set an Anthropic API key:

| Setting | Env var | Default |
|---|---|---|
| `dukkan.ai.anthropic.api-key` | `DUKKAN_ANTHROPIC_API_KEY` | empty: rules only |
| `dukkan.ai.anthropic.model` | `DUKKAN_ANTHROPIC_MODEL` | `claude-opus-5-5` |
| `dukkan.ai.anthropic.effort` | | `low` |
| `dukkan.ai.anthropic.timeout` | | `PT30S` per attempt, one retry |
| `dukkan.ai.anthropic.server-side-fallbacks` | | `true` (`fallbacks: "default"`: a declined request is retried by the API on its recommended model) |
| `dukkan.capture.drafts-per-hour` | | 30 per merchant and instance (429 `RATE_LIMITED`) |
| `dukkan.translation.requests-per-hour` | | 60 translation requests per merchant and instance (B-38) |
| `dukkan.shopping-list.requests-per-hour` | | 20 shopping lists per customer and instance (ADR 0015) |

The key is read only from this setting (not from `ANTHROPIC_API_KEY`). The same key enables machine
translation (ADR 0013): the "Fehlende Sprachen übersetzen" button and the background translation of new
private products, and lets customers' shopping lists be read from photos and short videos (ADR 0015;
without a key, spoken and typed lists are read by rules and the app says that photos and videos are not
available). With a key, merchant notes, photos and texts and customers' shopping lists are sent to
Anthropic; see ADR 0011, 0013 and 0015 before enabling it in production.

**Email confirmation** (B-39b): registration sends a link to `dukkan://verify-email?token=…` (valid 3 days,
`dukkan.security.auth.email-verification-ttl`); in the `local` profile the link is logged like the password reset
link. With `DUKKAN_REQUIRE_VERIFIED_EMAIL=true` (`dukkan.security.auth.require-verified-email`) unconfirmed accounts
cannot place orders or open markets (422 `EMAIL_NOT_VERIFIED`); it is off by default, so decide before launch.

**Sessions and accounts** (B-39): access tokens live 15 minutes (`DUKKAN_JWT_TTL`), refresh tokens 30 days and
rotate on every use; the app renews tokens by itself.

- **Password reset mails** need SMTP: `spring.mail.host`, `spring.mail.port`, `spring.mail.username`,
  `spring.mail.password` (env `SPRING_MAIL_HOST` …), sender `DUKKAN_MAIL_FROM`. The link is
  `DUKKAN_PASSWORD_RESET_URL?token=…` (default `dukkan://reset-password`, opened by the app). Without SMTP no mail
  is sent; the `local` profile writes the link to the log instead (`[dev] Password reset link for …`).
- **Sign in with Google / Apple**: create the OAuth clients (Google Cloud console: iOS, Android and web client;
  Apple Developer: the app's bundle id `app.dukkan.mobile` with the Sign in with Apple capability), then set
  `DUKKAN_GOOGLE_CLIENT_IDS` and `DUKKAN_APPLE_CLIENT_IDS` (comma separated) on the backend. In the app, plug a
  native adapter into the `SocialSignIn` port (`src/shared/auth/SocialSignIn.ts`) in `createDependencies()`; until
  then no provider buttons are shown. A sketch with `@invertase/react-native-apple-authentication` and
  `@react-native-google-signin/google-signin` (install them, run `pod install`, then build on a device):

  ```ts
  import { appleAuth } from '@invertase/react-native-apple-authentication';
  import { GoogleSignin, isSuccessResponse } from '@react-native-google-signin/google-signin';

  GoogleSignin.configure({ webClientId: '<web client id>', iosClientId: '<ios client id>' });
  export const nativeSocialSignIn: SocialSignIn = {
    providers: () => (Platform.OS === 'ios' && appleAuth.isSupported ? ['APPLE', 'GOOGLE'] : ['GOOGLE']),
    async signIn(provider) {
      try {
        if (provider === 'APPLE') {
          const r = await appleAuth.performRequest({ requestedOperation: appleAuth.Operation.LOGIN, requestedScopes: [appleAuth.Scope.EMAIL, appleAuth.Scope.FULL_NAME] });
          return r.identityToken ? { status: 'signedIn', idToken: r.identityToken, nonce: r.nonce, firstName: r.fullName?.givenName ?? undefined, lastName: r.fullName?.familyName ?? undefined } : { status: 'failed' };
        }
        const r = await GoogleSignin.signIn();
        return isSuccessResponse(r) && r.data.idToken ? { status: 'signedIn', idToken: r.data.idToken } : { status: 'cancelled' };
      } catch (e) {
        return (e as { code?: string }).code === appleAuth.Error.CANCELED ? { status: 'cancelled' } : { status: 'failed' };
      }
    },
  };
  ```
  The backend accepts the token's `nonce` raw or as SHA-256 hex, so either library's nonce handling works.

**Order events and notifications** (B-37, ADR 0012): order changes become inbox entries for merchants and
customers. By default the events are handled in-process (no broker). For several instances use RabbitMQ:
`docker compose -f infrastructure/docker/compose.yaml --profile events up -d rabbitmq` (management UI on
http://localhost:15672, user `dukkan` / `dukkan`) and start the backend with `DUKKAN_EVENTS_TRANSPORT=rabbitmq`
(`DUKKAN_RABBITMQ_URI`, default `amqp://dukkan:dukkan@localhost:5672`).

**Push notifications** use Firebase Cloud Messaging (Android, and iOS through the APNs key in Firebase):
create a Firebase project, a service account with the "Firebase Cloud Messaging API Admin" role, and set
`DUKKAN_FCM_PROJECT_ID` and `DUKKAN_FCM_CREDENTIALS_FILE` (path to the service account JSON, never committed).
In the app, plug an adapter into the `PushRegistration` port (`src/shared/push`) in `createDependencies()`, e.g.
with `@react-native-firebase/messaging`:

```ts
import messaging from '@react-native-firebase/messaging';

export const firebasePushRegistration: PushRegistration = {
  async deviceToken() {
    const status = await messaging().requestPermission();
    if (status !== messaging.AuthorizationStatus.AUTHORIZED && status !== messaging.AuthorizationStatus.PROVISIONAL) {
      return null;
    }
    return { token: await messaging().getToken(), platform: Platform.OS === 'ios' ? 'IOS' : 'ANDROID' };
  },
};
```
Without it, notifications still appear under Profil → Mitteilungen.

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

## 3a. Web portal (merchants and admins)

```bash
cd apps/web
npm ci
npm run dev   # http://localhost:5174; /api is proxied to http://localhost:8080 (override: DUKKAN_BACKEND_URL)
```

Sign in with `haendler@dukkan.local` (orders board, assortment, CSV import) or `admin@dukkan.local`
(market review). For the CSV import, "Vorlage herunterladen" gives a template that Excel opens with
the right columns; save as "CSV UTF-8". Checks: `npm run lint`, `npm test`, `npm run build`.

## 3b. Customer web app (the app in the browser)

```bash
cd apps/mobile && npm ci
cd ../customer-web && npm ci
npm run dev   # builds and serves http://localhost:5175; /api is proxied to http://localhost:8080
```

The customer app of `apps/mobile` built with react-native-web, with browser versions of storage,
location, photos, dictation, video frames and the map (Leaflet). Every screen has its own address.
Details, runtime settings and the nginx image: `apps/customer-web/README.md`. Checks:
`npm run typecheck`, `npm run lint`, `npm test`, `npm run smoke` (Chromium against the backend).

## 3c. Production

Production runs the `prod` profile (no demo data, review of new markets, https mail links,
startup check of the settings) on one server with `infrastructure/production` (Caddy with
automatic HTTPS, backend, web app, portal, PostgreSQL, nightly backup). Images come from the
release workflow (`git tag vX.Y.Z`). The whole plan, including what the operator has to provide:
`docs/launch/go-live.md`.

## 4. Quality checks

Native builds (B-28) run in `.github/workflows/native.yml` when `apps/mobile` changes, or by hand
(Actions → Native builds → Run workflow): an Android release APK (artifact
`dukkan-android-release-apk`, signed with the debug keystore, not for the stores) and an iOS
simulator build. Locally, `npm run bundle:check` in `apps/mobile` builds both release JS bundles and
compiles them to Hermes bytecode without an SDK.

| Check | Command | Result on this branch (2026-10-21) |
|---|---|---|
| Backend compile + Error Prone + all tests | `cd services/backend && ./gradlew clean build` | Pass: 207 tests in 40 classes, 0 failures (incl. security-review regressions, admin, rate limit, slot clean-up, media upload) |
| Contract conformance | part of the backend build: `ContractMappingTest`, `ResponseSchemaIntegrationTest` | Pass: every operation is mapped in both directions; responses have the required fields and no undocumented ones |
| Mobile type check (strict) | `cd apps/mobile && npm run typecheck` | Pass, 0 errors |
| Mobile lint | `npm run lint` | Pass, 0 problems |
| Mobile tests | `npm test` | Pass: 20 suites, 88 tests (live test skipped). Jest prints a worker-exit warning (open handle not yet found) |
| Visual QA (web preview) | `cd tools/web-preview && npm ci && npm run build && npm run screenshots` (backend running) | 59 screenshots in de/ar, light/dark; reviewed after each design change. Known preview artefacts: Latin text alignment and start/end paddings in Arabic, map placeholder |
| **Live API test (app client ↔ real backend)** | backend running, then `DUKKAN_LIVE_API_URL=http://localhost:8080/api/v1 npx jest live.api` | Pass: 3 tests covering discovery in 3 languages, cart, slot, order, merchant accept, cancel, a new 3-language product found via Arabic search, and a photo upload (PNG generated in the test) set as a listing image and served by `GET /media/{key}` |
| **System test (a market's life cycle, black box)** | backend running (`local` profile), then `DUKKAN_LIVE_API_URL=http://localhost:8080/api/v1 npx jest system.api` | Pass: 7 steps on fresh data per run (new merchant, market in Munich, new customer): admin hides and approves the market, shared-catalog listings with a "3 for 5 €" offer, a dictated shopping list matched with pack counts (1 kg → 2 × 500 g) and put into the cart, favourite, pickup order, missing items while packing (multi-buy re-priced, customer notified "statt 13,40 €"), no changes after handover, "Meine Märkte", best sellers and insights counting what was handed over, and ownership (404 for another merchant, 401 without a token) |
| **Web app smoke test (browser ↔ real backend)** | backend running (`local` profile), then `cd apps/customer-web && npm run build && npm run smoke` | Pass: 7 steps in Chromium: first visit and "use my location", map pins and card, market and product addresses that survive a reload, sign-in kept across a reload, add to cart and empty it through the confirmation dialog, Arabic right to left |
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
- **`e2e-api`**, which starts the backend against a PostgreSQL 18.6 service and runs the live API test and the system test;
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
