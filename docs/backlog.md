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
| M-15 | Visual QA through a react-native-web preview with screenshots (de/ar, light/dark), and fixing what it finds (14 defects fixed) | Done |
| M-16 | Security and correctness review of the backend, findings fixed with regression tests; admin API; rate limiting; slot clean-up; product icon placeholders | Done |
| B-29 | Dependency scanning: `.github/dependabot.yml` (weekly Gradle and npm updates with groups, monthly Bundler, Actions, Docker) and the Gradle dependency graph submitted to GitHub on `main` (`dependency-submission.yml`), so security alerts cover transitive backend dependencies too | Done (Dependabot alerts must be switched on in the repository settings) |
| B-28 | Native builds in CI: workflow `native.yml` (Android release APK with JDK 17 and the AGP-installed SDK 37 / NDK 27.1, uploaded as artifact; iOS Release build for the simulator on macOS 15 with CocoaPods from a committed `Gemfile.lock`, no signing); release JS bundles for both platforms compiled to Hermes bytecode in the regular mobile job (`npm run bundle:check`) | Done: first CI run on 2026-10-21 green (Android APK in 16 min, iOS simulator build in 9 min). Not run on devices yet; device checks and Detox: B-28a |
| B-36 | Admin market review in the web portal: markets by status, approve, suspend, return to review | Done (catalog moderation: B-36a) |
| B-31 | Merchant web portal `apps/web` (Vite, React 19, de/en/ar with RTL): orders board with status actions and auto-refresh, assortment with inline price and availability, CSV import (`POST /merchant/markets/{id}/listings/import`: Excel-style CSV with `;`/`,`, German column names, matching by barcode or name + size, dry run, all or nothing, re-import only updates); CI job `web` | Done (market profile editing stays in the app; promotions in the portal: later) |
| B-38 | Machine translation of merchant texts (ADR 0013): `POST /merchant/translations` suggests the empty locales (product name/description, promotion, market description), app button "Fehlende Sprachen übersetzen" in every multilingual form; new private products are completed in the background through the outbox (`catalog.private_product_created`), stored as machine translations (V9) and searchable in every language; Claude client shared with AI capture | Done (re-translation after edits and a review view: later) |
| B-37 | Order events through a transactional outbox (`order.placed`, `order.status_changed`), publisher with `SKIP LOCKED` batches and backoff; transports in-process (default) and RabbitMQ 4.3.6 (quorum queue, DLQ, publisher confirms); idempotent consumers; notification module with inbox (de/en/ar), device tokens and FCM HTTP v1 push; app inbox "Mitteilungen" with unread count, push registration port (ADR 0012) | Done (native push adapter: B-37a) |
| B-39 | Sessions and accounts: rotating refresh tokens with reuse detection, logout, 15-minute access tokens with silent renewal in the app (one refresh at a time, retry once); password reset by mail (SMTP, 30-minute single-use link, `dukkan://reset-password` deep link) and password change; Sign in with Apple/Google on the server (ID token verified against JWKS, account linking by verified email); rate limits for the new endpoints | Done (native provider adapters: B-39a) |
| B-32a | S3-compatible media storage (`dukkan.media.storage=s3`): `S3MediaStorage` on AWS SDK v2 (JDK HTTP client), conditional writes (`If-None-Match: *`), immutable `Cache-Control`, default credentials chain or static keys; S3Mock in Compose (profile `s3`) and in an integration test | Done |
| B-42 | Jest worker-exit warning: finished mutations scheduled React Query's 5-minute garbage-collection timer in the test clients; test clients now use `gcTime: Infinity` for mutations too | Done |
| B-40 | Opening hours across midnight (`18:00–02:00`; migration V6) and several ranges per day in the app editor; overlap check in API and app (also Sunday → Monday); `openNow`, today's hours and slots follow overnight ranges | Done |
| B-33 | AI product capture ("Schnell erfassen"): `POST /merchant/markets/{id}/product-drafts` turns a note (typed or dictated with the keyboard) and/or a photo into a draft (de/en/ar names, size, category, cuisines, EU allergens, price, barcode) plus shared catalog matches; Claude API (`claude-opus-5-5`, structured output, server-side fallbacks) behind a port, rule-based parser when there is no key or the model fails (ADR 0011); app panel fills the new-product form; allergens as localised chips | Done (video and several photos per draft: later) |
| B-32 | Image upload for merchants: `POST /merchant/markets/{id}/media` (JPEG/PNG/WebP by magic bytes, 5 MB, 40 MP, 60/h per user), content-addressed filesystem storage behind `MediaStorage`, public `GET /media/{key}`; listing photos (`UpdateListingRequest.imageUrl`); app "Foto wählen" for logo, cover, new and existing products | Done (filesystem storage; S3 is B-32a) |

## P1: Next

| ID | Task | Depends on | Est. | Notes |
|---|---|---|---|---|
| B-32b | CDN in front of the bucket, optional server-side thumbnails/WebP and EXIF stripping, banner management API, migration of existing files from the volume to the bucket | B-32a | M | Banner photos can already be uploaded with purpose BANNER |
| B-33a | AI capture follow-ups: drafts from several photos or a short video, barcode lookup against open data, Anthropic in the privacy notice/DPA before enabling the key in production, shared rate-limit store | B-33 | M | ADR 0011 |
| B-34 | Grow the shared catalog per cuisine (Turkish, Arabic, Indian, Polish, …) with barcode, allergens and images | M-05 | L | Data work. Consider open data such as Open Food Facts (licence check) |
| B-35 | Real geocoding and maps provider (Google, Mapbox or OSM-based) behind `GeocodingService`; Android Maps key | M-03 | M | Decision pending (product decision 15) |
| B-36a | Moderation of shared catalog products in the admin portal (review merchant-created products, promote good ones to the shared catalog) | B-36 | M | Market review in the portal is done (B-36). Turn `merchant-auto-approve` off in production |
| B-37a | Firebase project and the native `PushRegistration` adapter (docs/setup.md), deep links from a push to the order; outbox retention job; notification preferences per user | B-37, B-28 | S | Needs a Firebase project (product owner) |
| B-39a | Native Apple/Google adapters behind the `SocialSignIn` port once the OAuth clients exist (docs/setup.md has the adapter); shared (Redis) rate-limit store for several replicas; optional e-mail verification at registration | B-39, B-28 | S | Needs Apple Developer and Google Cloud accounts (product owner) |
| B-41 | Paging the `openNow` and name filters in SQL instead of Java; promotional price in `price_asc` sorting | M-04, M-05 | S | |
| B-28a | Detox end-to-end tests on emulator/simulator; check fonts, RTL and permissions on devices (Arabic sheet title padding, tab badge position); signed release builds (upload keystore, App Store Connect) | B-28 | M | Needs green native jobs first, and signing secrets from the product owner |
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
