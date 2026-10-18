# Development backlog

Estimates: **S** ≤ 2 days, **M** 3–5 days, **L** > 5 days. Priority: **P0** is the MVP, **P1** comes next, **P2** later.
"Done" items reflect what this branch implements; see `docs/setup.md` for how they were verified.

## P0: MVP (this branch)

| ID | Task | Depends on | Est. | Status |
|---|---|---|---|---|
| B-01 | Monorepo layout, version pinning, lockfiles | none | S | Done |
| B-02 | OpenAPI contract and business rules | none | S | Done |
| B-03 | Backend skeleton: modules, error handling, logging, config | B-01 | M | Done |
| B-04 | Flyway schema and invented seed data | B-03 | M | Done |
| B-05 | Auth: register, login, JWT, `/me` | B-03 | M | Done |
| B-06 | Catalog, search and suggestions | B-04 | M | Done |
| B-07 | Promotions and pricing engine | B-04 | M | Done |
| B-08 | Cart | B-06, B-07 | M | Done |
| B-09 | Addresses and delivery-area check | B-05 | S | Done |
| B-10 | Mock slot generation, reservation and capacity lock | B-09 | M | Done |
| B-11 | Simulated checkout and orders, with cancellation | B-08, B-10 | M | Done |
| B-12 | Mobile app: navigation, API client, auth with secure storage | B-02 | M | Done |
| B-13 | Mobile app: catalog, search, product, promotions screens | B-12 | M | Done |
| B-14 | Mobile app: cart, address, slot picker, checkout, orders | B-13 | L | Done |
| B-15 | CI: backend build and tests, mobile typecheck, lint and tests, audit | B-03, B-12 | S | Done |

## P1: hardening and the next features

| ID | Task | Depends on | Est. | Notes |
|---|---|---|---|---|
| B-16 | Get a Picnic APK lawfully and run the REA plan; add P-xx evidence | Network allow-list, device, JADX JAR | M | See `docs/reverse-engineering/technical-analysis.md` |
| B-17 | Refresh tokens, login rate limiting, password reset | B-05 | M | ADR 0006 |
| B-18 | Recipes: browse, then add all ingredients to the cart | B-08 | M | Tables already in V1 schema. Reported feature A-08 |
| B-19 | Waitlist for postal codes we don't serve yet | B-09 | S | Reported feature A-09 |
| B-20 | Order events on RabbitMQ (`order.placed`, `order.cancelled`) via a transactional outbox | B-11 | M | Triggers RabbitMQ adoption (ADR 0002) |
| B-21 | Push notifications (FCM/APNs) for order status | B-20 | M | |
| B-22 | Delivery simulator and live tracking over SSE; 20-minute window on delivery day | B-20 | L | Possible Reactor use case |
| B-23 | Payment provider integration (sandbox) | B-11 | L | Separate security review |
| B-24 | Server-driven home and deals layout, or OTA updates | B-13 | L | ADR 0004 |
| B-25 | Demand and slot-capacity forecasting (Python, scikit-learn, Snowflake) | B-11 order history | L | ADR 0002 |
| B-26 | Product images: asset pipeline and CDN | B-06 | M | The MVP uses placeholder tiles |
| B-27 | Full-text search (PostgreSQL `tsvector`, German dictionary) and typo tolerance | B-06 | M | |
| B-28 | Android and iOS native builds in CI (needs SDK runners), plus Detox end-to-end tests | B-14 | M | Native builds were not possible in the sandbox |
| B-29 | OWASP dependency-check or Dependabot for the Gradle dependencies | B-15 | S | |
| B-30 | Production infrastructure: managed Postgres, ingress, TLS, External Secrets | B-15 | L | |

## P2

Order editing after placement, substitutions, deposit returns, the shopping
list as a separate concept from the cart, i18n (de/en), web shop, and an
admin back office.
