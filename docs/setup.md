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
The `local` profile has a development-only JWT secret and plain-text logs.
Flyway creates the schema and loads the invented seed data on startup.

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
The API base URL comes from `app.json` (`apiBaseUrl`). The defaults are
`http://10.0.2.2:8080/api/v1` on the Android emulator and
`http://localhost:8080/api/v1` on iOS. Register a new account in the app; you
can use any of the seeded postal codes for the address (for example `41460`,
`40545`, `50667`).

## 4. Quality checks

| Check | Command | Result on this branch (2026-10-18) |
|---|---|---|
| Backend compile + Error Prone + all tests | `cd services/backend && ./gradlew clean build` | Pass: 96 tests, 0 failures, 0 skipped |
| Mobile type check (strict) | `cd apps/mobile && npm run typecheck` | Pass, 0 errors |
| Mobile lint | `npm run lint` | Pass, 0 problems |
| Mobile tests | `npm test` / `npm run test:ci` | Pass: 8 suites, 41 tests (53% statement coverage) |
| Metro release bundle | `npx react-native bundle --platform android --dev false …` | Built (about 1.4 MB, Android and iOS) |
| Contract lint | `npx -y @redocly/cli@2.64.1 lint packages/shared-contracts/openapi.yaml` | Valid, 0 errors, 12 warnings (public operations without a 4xx response) |
| Mermaid diagrams | `@mermaid-js/mermaid-cli@11.12.0` on every block | All 9 render |
| Kubernetes manifests | `kubectl kustomize infrastructure/kubernetes` | Renders |
| Dependency audit (mobile, runtime deps) | `npm audit --omit=dev` | **20 high**, all from one advisory, `braces <=3.0.3` (GHSA-vfj7-8cjw-p6xm). It comes in through metro and the RN CLI (tooling, not the app bundle). No fix is published |
| End-to-end smoke test | Backend jar + Compose Postgres, curl through register → cart → slot → order → cancel | Pass (see `docs/api/README.md`) |
| Native Android/iOS builds | none | **Not run.** No Android SDK or Xcode in the build sandbox (backlog B-28) |
| Backend dependency vulnerability scan | none | **Not run** (backlog B-29) |

CI (`.github/workflows/ci.yml`) runs the contract lint, the backend build and
image build, the mobile type check, lint, tests and audit, and the manifest
render. The audit step fails only on **critical** advisories. The known high
`braces` advisory is documented above rather than blocking every build.

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
