# dukkan

Dukkan is a marketplace for local markets: supermarkets, international
groceries, bakeries, butchers and more. Customers find markets near them on a
list or a map. Every market opens as its own "sub-app" with a start page,
favourites, search and a cart. Merchants manage their market, products,
offers and orders. The app supports German, English and Arabic (RTL), with
light and dark themes. The launch city is Hamburg.

| Path | What |
|---|---|
| `apps/mobile` | React Native 0.88 · React 19.3 · TypeScript 6.0 (strict) · i18next |
| `services/backend` | Java 25 · Spring Boot 4.1 · Gradle 9.8 · Error Prone · Flyway · PostgreSQL 18.6 |
| `packages/shared-contracts` | OpenAPI 0.2 contract and business rules (source of truth) |
| `infrastructure/docker`, `infrastructure/kubernetes` | Local Compose stack, minimal K8s 1.36 manifests |
| `tools/web-preview` | react-native-web preview, for visual QA screenshots only |
| `docs/product` | Product decisions (marketplace) |
| `docs/design` | Design system (tokens, typography, RTL, screen specs) |
| `docs/architecture` | C4 overview, database design and ERD, ADRs, diagrams |
| `docs/api`, `docs/setup.md`, `docs/backlog.md` | API guide with real examples, setup and QA results, backlog |
| `docs/reverse-engineering` | The initial evidence-labelled study that informed the feature set |

Quick start (details and demo accounts in [docs/setup.md](docs/setup.md)):

```bash
docker compose -f infrastructure/docker/compose.yaml up -d postgres
(cd services/backend && ./gradlew bootRun --args='--spring.profiles.active=local')
(cd apps/mobile && npm ci && npm start)
```

All markets, products, people and addresses in the seed data are invented.
