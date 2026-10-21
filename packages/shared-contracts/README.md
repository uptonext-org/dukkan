# Shared contracts

`openapi.yaml` is the contract-first API definition for the Dukkan marketplace. Both
`services/backend` and `apps/mobile` build against it, and a change to it updates both
sides in the same change. The backend's contract test fails when an operation has no
handler, or when an `/api/v1` handler is not documented here.

Product decisions behind it: [`docs/product/marketplace.md`](../../docs/product/marketplace.md).

## Business rules

The server is authoritative. The app only displays the prices, totals and
availability it receives.

| Rule | Value (default, configurable in the backend) |
|---|---|
| Locales | Enabled in `supported_locale`: `de` (fallback), `en`, `ar` (RTL). Adding a row (plus app strings) adds a language |
| Content fallback | Requested locale → market primary locale → `de` → any existing translation |
| Search | Case- and accent-insensitive substring match over **all** translations of name and brand, plus keywords. Results show in the requested locale |
| Currency | EUR, amounts in cents. Number formatting in clients is `de-DE` with Latin digits in every language |
| Market radius search | Great-circle distance from `lat/lng`. Default radius 10 km, max 50. Only `ACTIVE` markets |
| Delivery area | Circle of `deliveryRadiusKm` around the market |
| Open now | Opening hours in `Europe/Berlin`. A market can have several intervals per day |
| Minimum order value | Per market, after discounts, excluding delivery fee. Applies to delivery **and** pickup |
| Delivery fee | Market `deliveryFee`, or 0 when the subtotal after discounts ≥ `freeDeliveryFrom`. Pickup is always 0 |
| Max quantity per line | `maxOrderQuantity` per listing (default 24, hard cap 99). Checked when setting a quantity **and again at checkout** |
| Slots | The next 7 days, Monday to Saturday, during opening hours. 1-hour windows. Capacity 6 for delivery and 10 for pickup per window (defaults) |
| Order cutoff | Delivery: 2 h before the window starts. Pickup: 1 h before |
| Reservation | Only for a non-empty cart (`CART_EMPTY`). Holds capacity until the cutoff, or until the cart is cleared, emptied or the reservation replaced |
| Cart | One per user per market. Placing an order empties only that market's cart |
| Idempotency-Key | Scoped to the user. Reusing it in the same market replays the order; in another market → 409 `IDEMPOTENCY_KEY_REUSED` |
| Passwords | 10–128 characters and at most 72 UTF-8 bytes (bcrypt) |
| Rate limits | Per client IP and instance: 20 logins or social sign-ins per minute, 10 registrations per hour, 60 token refreshes per minute, 5 password reset requests per hour → 429 `RATE_LIMITED` with `Retry-After` |
| Sessions | Access token (JWT) 15 minutes; refresh token 30 days, opaque, single use: `/auth/refresh` returns a new pair and spends the old token. A spent token presented again revokes every token of that sign-in (`INVALID_REFRESH_TOKEN`). Logout, password reset and password change revoke refresh tokens; access tokens expire on their own |
| Password reset | `/auth/password-reset` always answers 202. The mailed link (`dukkan://reset-password?token=…`) is valid 30 minutes and once; the new password follows the registration rules (10–128 characters, ≤ 72 UTF-8 bytes) |
| Sign in with Apple/Google | ID token verified against the provider's JWKS: RS256, issuer, audience = configured client IDs, expiry (60 s skew), optional nonce (raw or SHA-256 hex). Known provider account → sign in; else link an account with the same verified email; else create one (`SOCIAL_EMAIL_MISSING` without a verified email). Provider without client IDs → 422 `SOCIAL_PROVIDER_DISABLED` |
| Machine translation (`POST /merchant/translations`) | Suggestions for the empty enabled locales of each field, nothing saved; new private products are translated in the background (outbox event `catalog.private_product_created`), stored as machine translations, never overwriting merchant text. No language model → 422 `TRANSLATION_UNAVAILABLE` |
| Amounts in requests | 0–10,000,000 cents |
| Promotions | `validUntil` after `validFrom` and in the future |
| Cancellation (customer) | `PLACED` or `ACCEPTED`, before the cutoff |
| Order status (merchant) | PLACED→ACCEPTED or REJECTED; ACCEPTED→READY_FOR_PICKUP (pickup) or OUT_FOR_DELIVERY (delivery); those → COMPLETED. REJECTED and CANCELLED release capacity |
| PERCENT_OFF | Per unit: `round_half_up(unitPrice * percentOff / 100)` off. `promotionalPrice = unitPrice - discount` |
| MULTI_BUY | Each complete group of `bundleQuantity` units costs `bundlePrice`; remaining units cost full price |
| Promotion stacking | At most one active promotion per listing. The most recently created one wins |
| "New" | A listing created within the last 14 days |
| Frequently ordered | A listing that appears in ≥ 2 of the user's non-cancelled orders in that market. Shown in favourites with reason `FREQUENTLY_ORDERED` |
| Image upload | `POST /merchant/markets/{id}/media` (multipart `file` + `purpose`). JPEG, PNG or WebP detected from the bytes (declared type ignored) → else 415 `UNSUPPORTED_MEDIA_TYPE`; ≤ 5 MB → else 413 `FILE_TOO_LARGE`; must decode → else 400 `INVALID_IMAGE`; ≤ 40 megapixels → else 400 `IMAGE_TOO_LARGE`; 60 uploads per user per hour → 429. Key = SHA-256 of the bytes + extension (same bytes, same URL). `GET /media/{key}` is public and immutable |
| Product drafts (AI capture) | `POST /merchant/markets/{id}/product-drafts` with `text` (≤ 1000) and/or `imageKey` (an uploadMedia key; unknown → 400 field `imageKey` code `NotFound`), at least one → else 400. Nothing is saved. `engine` AI (language model) or RULES (rule-based parser: no key, model failed or declined); `warnings` say why and what is missing. Category, cuisines, allergens (14 EU codes), `netUnit` and lengths are restricted to known values; price only when stated (1–10,000,000 cents). Up to 5 shared catalog matches. 30 drafts per merchant per hour → 429 |
| Notifications | Merchants of a market: `NEW_ORDER`, `ORDER_CANCELLED` (by the customer). Customers: `ORDER_ACCEPTED`, `ORDER_REJECTED`, `ORDER_READY_FOR_PICKUP`, `ORDER_OUT_FOR_DELIVERY`, `ORDER_COMPLETED` (merchant actions only). Text in the recipient's preferred language at creation time; amounts in German format. Arrive within about a second (outbox), at least once, stored once. Push to the 10 most recently seen devices of the user |
| Listing photo | `UpdateListingRequest.imageUrl`: absent = unchanged, `null` = remove. For a shared catalog product it applies to this market's listing only |
| Merchant scope | Merchants can only read and change their own markets, listings, promotions and orders (other markets return 404) |

All seed data (markets in Hamburg, products, prices, people) is invented test data.
None of it comes from a real retailer or person.
