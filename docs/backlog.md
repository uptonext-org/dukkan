# Development backlog

Estimates: **S** ≤ 2 days, **M** 3–5 days, **L** > 5 days. Priority: **P0** is the current phase 1, **P1** comes next, **P2** later.
Product phases: [`docs/product/marketplace.md`](product/marketplace.md).

## P0: Phase 1, the marketplace MVP (this branch)

| ID | Task | Status |
|---|---|---|
| M-01 | Product decisions, design system, contract v0.2, ADRs 0008–0010 | Done |
| M-02 | Backend: i18n (locales, translation tables, fallback, Content-Language) | Done |
| M-03 | Backend: geo (radius search, gazetteer geocoding, punctuation-tolerant address search) | Done |
| M-04 | Backend: markets (types, cuisines, hours, banners, home page) | Done |
| M-05 | Backend: shared catalog and listings, multilingual search with relevance tiers | Done |
| M-06 | Backend: cart per market, delivery and pickup slots, orders, merchant status machine | Done |
| M-07 | Backend: favourites (manual and frequently ordered) | Done |
| M-08 | Backend: merchant API with ownership checks, roles, feature flags | Done |
| M-09 | Seed data for Hamburg (12 markets, 158 products in de/en/ar) and demo accounts | Done |
| M-10 | App: i18n de/en/ar, RTL, fonts, light and dark theme | Done |
| M-11 | App: onboarding, marketplace shell (list, map, filters, cross-market search, carts, profile) | Done |
| M-12 | App: market sub-app (home, favourites, search, cart, fulfilment, order) | Done |
| M-13 | App: merchant mode (profile, listings from catalog or new, promotions, orders) | Done |
| M-14 | Live API test (app client against the backend) plus CI job `e2e-api` | Done |
| M-15 | Visual QA through a react-native-web preview with screenshots (de/ar, light/dark), and fixing what it finds | In progress |

## P1: Next

| ID | Task | Depends on | Est. | Notes |
|---|---|---|---|---|
| B-31 | Merchant web portal (Phase 2): profile, bulk product import (CSV), orders board | M-08 | L | Mobile merchant mode stays for quick actions |
| B-32 | Image upload (S3-compatible object storage + CDN, MinIO locally), product, logo, cover and banner photos | M-08 | M | Replaces the URL fields |
| B-33 | AI product capture: voice, photo or video becomes a `catalog_product` draft that the merchant confirms | B-32 | L | Through the shared-catalog model (ADR 0009). Provider TBD |
| B-34 | Grow the shared catalog per cuisine (Turkish, Arabic, Indian, Polish, …) with barcode, allergens and images | M-05 | L | Data work. Consider open data such as Open Food Facts (licence check) |
| B-35 | Real geocoding and maps provider (Google, Mapbox or OSM-based) behind `GeocodingService`; Android Maps key | M-03 | M | Decision pending (product decision 15) |
| B-36 | Admin UI (web) for market review, plus moderation of shared catalog products | M-08 | M | **Admin API for reviewing, approving and suspending markets is done.** Turn `merchant-auto-approve` off in production |
| B-37 | Order events through a transactional outbox to RabbitMQ; push notifications to customer and merchant | M-06 | M | RabbitMQ adoption trigger (ADR 0002) |
| B-38 | Machine translation of merchant content (DeepL or similar) into missing locales, editable | M-02 | M | Through the existing translation tables |
| B-39 | Refresh tokens, login rate limiting, password reset, Sign in with Apple and Google | — | M | Apple requires Sign in with Apple when other social logins exist |
| B-40 | Opening hours across midnight, several intervals per day in the app editor | M-04 | S | Nightly slot clean-up job is done (`SlotCleanupJob`, 03:15 Berlin) |
| B-41 | Paging the `openNow` and name filters in SQL instead of Java; promotional price in `price_asc` sorting | M-04, M-05 | S | |
| B-42 | Find and fix the Jest worker-exit warning in the full-app suites | M-12 | S | |
| B-28 | Android and iOS native builds in CI (SDK runners), Detox end-to-end tests, check fonts, RTL and permissions on devices | M-10 | M | |
| B-29 | Dependency scanning for Gradle (OWASP dependency-check or Dependabot) | — | S | |
| B-16 | (Reverse engineering) Get a Picnic APK lawfully and run the REA plan | user-supplied APK | M | `docs/reverse-engineering/technical-analysis.md` |

## P2: Later

| ID | Task | Notes |
|---|---|---|
| B-43 | Real payments with split payouts (Stripe Connect, Adyen for Platforms or Mangopay), refunds, invoices | Regulatory review |
| B-44 | Delivery logistics: driver app or partner integration, live tracking (Reactor/SSE) | Product decision 6 is open |
| B-45 | Ratings and reviews of markets and products | DSA and P2B compliance |
| B-46 | Business model: commission or fees, merchant billing | Product decision 12 is open |
| B-47 | Login required to open the app (flip `require-login-to-browse`) | Product decision 14 |
| B-48 | Generated storefront image of a market from a photo of its shop front | Product decision 21 |
| B-49 | More languages (Turkish, Polish, …): a `supported_locale` row plus one app JSON file | ADR 0008 |
| B-50 | Demand and capacity forecasting (Python, scikit-learn, Snowflake) | ADR 0002 |
