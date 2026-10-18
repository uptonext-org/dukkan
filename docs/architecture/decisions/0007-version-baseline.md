# ADR 0007: Version baseline and deviations

- **Status:** Accepted, 2026-10-18

Every requested version was checked against its official registry on
2026-10-18. The full per-package records, with sources, are in
`services/backend/VERSIONS.md` and `apps/mobile/VERSIONS.md`.

| Component | Requested | Used | Note |
|---|---|---|---|
| Node.js | 24.22.0 | 24.22.0 | Verified against nodejs.org SHASUMS256 |
| React Native / React | 0.88.0 / 19.3.1 | same | RN 0.88.0 was released 2026-10-15, three days before this work. The template pins React 19.3.0; we bumped it to 19.3.1 |
| TypeScript | 6.0.3 | 6.0.3 | 7.0.2 is `latest` but was not adopted, as instructed. typescript-eslint 8.71.2 accepts TS `>=4.8.4 <6.1.0` |
| openapi-typescript | none | **not used** | 7.13.0 needs TS ^5. Mobile API types are hand-written to mirror the contract |
| Jest / Babel | none | 29.7 / 7.29 | RN 0.88's presets are still on these majors |
| JDK | 25.0.4 | Temurin **25.0.4.1+1** | Temurin's 25.0.4 build is published as 25.0.4.1 |
| Spring Boot | 4.1.1 | 4.1.1 | Its BOM pins Reactor 2025.0.7, Spring AMQP 4.1.1, MongoDB driver 5.8.1 and Kotlin 2.3.21, all matching the request. No overrides |
| Gradle | 9.8.1 | 9.8.1 wrapper | `distributionSha256Sum` was computed by us because the proxy blocked the official checksum URL. Re-check it against gradle.org |
| Error Prone / EP Support | 2.50.0 / 0.30.0 | same | Works on JDK 25 through net.ltgt.errorprone 5.1.1. We excluded Refaster rules that rewrite code to Guava, because Guava is not a dependency |
| PostgreSQL | 18.6 | `postgres:18.6` | Testcontainers and Compose |
| Kubernetes | 1.36.5 | kubectl 1.36.5 (render only) | Not deployed to a cluster |
| MongoDB, RabbitMQ, Python stack, Kotlin | as listed | not used | See ADR 0002 |
