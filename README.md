# dukkan

Dukkan is an independent grocery-delivery MVP: a React Native app and a
Spring Boot modular monolith on PostgreSQL. Its features were informed by a
documented, evidence-labelled study of a public grocery app. All code, names
and data are original.

| Path | What |
|---|---|
| `apps/mobile` | React Native 0.88 · React 19.3 · TypeScript 6.0 (strict) |
| `services/backend` | Java 25 · Spring Boot 4.1 · Gradle 9.8 · Error Prone · Flyway |
| `packages/shared-contracts` | OpenAPI 3.1 contract and business rules (source of truth) |
| `infrastructure/docker`, `infrastructure/kubernetes` | Local Compose stack, minimal K8s 1.36 manifests |
| `docs/reverse-engineering` | Investigation report, evidence register, features and spec, flows |
| `docs/architecture` | C4 overview, database design and ERD, ADRs, diagrams |
| `docs/api`, `docs/backlog.md`, `docs/setup.md` | API guide with real examples, backlog, setup and QA results |

Quick start: see [docs/setup.md](docs/setup.md).

```bash
docker compose -f infrastructure/docker/compose.yaml up -d postgres
(cd services/backend && ./gradlew bootRun --args='--spring.profiles.active=local')
(cd apps/mobile && npm ci && npm start)
```
