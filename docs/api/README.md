# API specification (v0.2, marketplace)

- **Contract (source of truth):** [`packages/shared-contracts/openapi.yaml`](../../packages/shared-contracts/openapi.yaml). OpenAPI 3.1, `@redocly/cli@2.64.1` lint: 0 errors.
- **Business rules:** [`packages/shared-contracts/README.md`](../../packages/shared-contracts/README.md)
- **Live docs (local):** http://localhost:8080/swagger-ui.html and `/v3/api-docs`
- **Base URL:** `http://localhost:8080/api/v1` (`10.0.2.2` from the Android emulator)
- **Languages:** send `Accept-Language: de|en|ar`. Responses carry `Content-Language`.

## Endpoints

| Area | Operations | Auth |
|---|---|---|
| Meta | `GET /locales`, `/market-types`, `/cuisines`, `/categories` | public |
| Auth | `POST /auth/register` (customer or merchant), `POST /auth/login`, `GET/PATCH /me` | public / JWT |
| Geo | `GET /geo/search?q=`, `GET /geo/reverse?lat=&lng=` | public |
| Markets | `GET /markets?lat=&lng=&radiusKm=&types=&cuisines=&fulfilment=&openNow=&hasOffers=&sort=`, `GET /markets/{id}`, `GET /markets/{id}/home` | public* |
| Market catalog | `GET /markets/{id}/categories`, `/products`, `/search/suggestions`, `/promotions`; `GET /products/{id}` | public* |
| Cross-market search | `GET /search/products?q=&lat=&lng=&sort=relevance\|price_asc\|distance` | public* |
| Favourites | `GET /favorites/markets`, `PUT/DELETE /favorites/markets/{id}`, `GET /markets/{id}/favorites`, `PUT/DELETE /favorites/products/{id}` | JWT |
| Carts | `GET /carts`, `GET/DELETE /markets/{id}/cart`, `PUT/DELETE /markets/{id}/cart/items/{productId}`, `PUT /markets/{id}/cart/fulfilment` | JWT |
| Slots | `GET /markets/{id}/slots?type=DELIVERY\|PICKUP[&addressId=]` | public* (JWT with addressId) |
| Addresses | `GET/POST /addresses`, `PUT/DELETE /addresses/{id}` | JWT |
| Orders | `POST /markets/{id}/orders`, `GET /orders`, `GET /orders/{id}`, `POST /orders/{id}/cancel` | JWT |
| Admin | `GET /admin/markets?status=`, `POST /admin/markets/{id}/status` (ACTIVE / SUSPENDED / PENDING_REVIEW) | JWT + ADMIN |
| Merchant | `GET/POST /merchant/markets`, `PUT /merchant/markets/{id}`, `GET /merchant/catalog?marketId=`, listings (CRUD, `imageUrl` on update), promotions, `GET /merchant/markets/{id}/orders`, `POST /merchant/orders/{id}/status`, `POST /merchant/markets/{id}/media` (photo upload), `POST /merchant/markets/{id}/product-drafts` (AI capture) | JWT + MERCHANT |
| Media | `GET /media/{sha256}.{jpg\|png\|webp}` (uploaded images, long-lived cache) | public (always) |

\* Public while `dukkan.features.require-login-to-browse=false` (phase 1 default).

## Examples

Captured on 2026-10-21 from a local run: backend jar with the `local` profile,
Compose PostgreSQL 18.6, seed data. Arrays are cut to two entries; generated
ids will differ.

### Address search (onboarding)
`GET /geo/search?q=st%20georg&limit=2`. Punctuation and word order don't matter:
```json
[{"label":"St. Georg, Hamburg","postalCode":"20099","district":"St. Georg","city":"Hamburg","location":{"lat":53.5551,"lng":10.0128}},
 {"label":"20099 Hamburg (St. Georg)","postalCode":"20099","district":"St. Georg","city":"Hamburg","location":{"lat":53.5551,"lng":10.0128}}]
```

### Markets near a location
`GET /markets?lat=53.5553&lng=10.0123&radiusKm=5&types=GROCERY&size=1`
```json
{"items":[{"slug":"anatolia-frischmarkt","name":"Anatolia Frischmarkt",
  "type":{"code":"GROCERY","name":"Lebensmittelladen","iconKey":"basket"},
  "cuisines":[{"code":"TURKISH","name":"Türkisch"},{"code":"ARABIC","name":"Arabisch"}],
  "addressLine":"Kardamomweg 7, 20099 Hamburg","distanceMeters":197,"deliversToLocation":true,
  "openNow":true,"todayHours":"08:00–20:00",
  "fulfilment":{"delivery":true,"pickup":true,"deliveryRadiusKm":4.0,
    "deliveryFee":{"amountCents":290,"currency":"EUR"},"freeDeliveryFrom":{"amountCents":4000,"currency":"EUR"},
    "minimumOrderValue":{"amountCents":1500,"currency":"EUR"}},
  "activePromotionCount":3,"tags":["HALAL"]}],
 "page":0,"size":1,"totalItems":4,"totalPages":4}
```

### Market start page in Arabic
`GET /markets/{id}/home` with `Accept-Language: ar` returns `Content-Language: ar`:
```json
{"banners":[{"overline":"لفترة محدودة","title":"عروض الأسبوع","tint":"sage","target":{"type":"PROMOTIONS","id":null}}],
 "sections":[{"key":"PROMOTIONS","title":"العروض","items":[{"name":"باذنجان","unitSize":"500 g",
   "price":{"amountCents":199,"currency":"EUR"},"promotionalPrice":{"amountCents":169,"currency":"EUR"},
   "promotion":{"label":"خصم 15%","type":"PERCENT_OFF"}}]}]}
```

### Product search across markets in any language
`GET /search/products?q=تفاح&lat=53.5553&lng=10.0123` with `Accept-Language: de`
returns 26 hits, best match first, with the price at each market:
```json
[{"product":"Äpfel Elstar","price":{"amountCents":299,"currency":"EUR"},"market":"Anatolia Frischmarkt","distanceMeters":197},
 {"product":"Äpfel Elstar","price":{"amountCents":269,"currency":"EUR"},"market":"Smaki Polskie","distanceMeters":3096}]
```
`apfel`, `Äpfel` and `apple` find the same listings, because search runs over
every translation and keyword.

### Cart, slot and order (one market)
```bash
TOKEN=$(curl -s -H 'Content-Type: application/json' \
  -d '{"email":"kunde@dukkan.local","password":"dukkan-demo-2026"}' localhost:8080/api/v1/auth/login | jq -r .accessToken)
curl -s -X PUT -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"quantity":3}' localhost:8080/api/v1/markets/$MARKET/cart/items/$PRODUCT        # → Cart of that market
curl -s "localhost:8080/api/v1/markets/$MARKET/slots?type=PICKUP"                       # → Slot[]
curl -s -X PUT -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"type\":\"PICKUP\",\"slotId\":\"$SLOT\"}" localhost:8080/api/v1/markets/$MARKET/cart/fulfilment
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H 'Idempotency-Key: k1' \
  localhost:8080/api/v1/markets/$MARKET/orders                                         # → 201 Order (PLACED)
```
The whole flow, including the merchant accepting the order, the customer
cancelling, and a new three-language product found by an Arabic search, is
automated in `apps/mobile/src/shared/api/__tests__/live.api.test.ts`. It runs
the app's own API client against the backend and also runs in CI as job
`e2e-api`.

### Photo upload (merchant)
`multipart/form-data` with a `file` part and a `purpose` field (`LOGO`, `COVER`, `BANNER`, `PRODUCT`). The
format is detected from the bytes (JPEG, PNG, WebP); the part's declared content type is ignored.
```bash
TOKEN=$(curl -s -H 'Content-Type: application/json' \
  -d '{"email":"haendler@dukkan.local","password":"dukkan-demo-2026"}' localhost:8080/api/v1/auth/login | jq -r .accessToken)
curl -s -H "Authorization: Bearer $TOKEN" -F file=@photo.png -F purpose=PRODUCT \
  localhost:8080/api/v1/merchant/markets/$MARKET/media                                  # → 201 + Location
```
```json
{"key":"3fd6e6be528c182d768563a63b65ac5a70d022149a01eeeaaa30396d75f426e0.png",
 "url":"http://localhost:8080/api/v1/media/3fd6e6be528c182d768563a63b65ac5a70d022149a01eeeaaa30396d75f426e0.png",
 "width":4,"height":4,"contentType":"image/png","sizeBytes":73}
```
Put `url` into `logoUrl` / `coverImageUrl` (`PUT /merchant/markets/{id}`), `newProduct.imageUrl` (new private
product) or `imageUrl` of `PUT /merchant/markets/{id}/listings/{productId}` (`null` removes the market's photo; a
shared catalog image then shows again). The same bytes always give the same URL.

| Status | `code` | When |
|---|---|---|
| 201 | | stored (or already stored) |
| 400 | `VALIDATION_FAILED` | `file` or `purpose` missing/unknown |
| 400 | `INVALID_IMAGE` | JPEG/PNG does not decode completely, WebP header inconsistent, empty file |
| 400 | `IMAGE_TOO_LARGE` | more than 40 megapixels |
| 403 / 404 | `FORBIDDEN` / `NOT_FOUND` | not a merchant / not the owner of the market |
| 413 | `FILE_TOO_LARGE` | more than 5 MB |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | not JPEG, PNG or WebP by content (GIF, SVG, HEIC, HTML …), or the body is not multipart |
| 429 | `RATE_LIMITED` | more than 60 uploads per user and hour (`Retry-After`) |

`GET /media/{key}` answers with the stored bytes, `Content-Type` from the detected format,
`Cache-Control: max-age=31536000, public, immutable`, an `ETag` (304 on `If-None-Match`),
`X-Content-Type-Options: nosniff` and `Content-Security-Policy: default-src 'none'; sandbox`.

### Product draft (AI capture)
`POST /merchant/markets/{id}/product-drafts` turns a note and/or an uploaded photo (`imageKey`) into a
draft that is not saved. Captured from the local run without an API key (`engine: RULES`);
`catalogMatches` is cut to one entry:
```bash
curl -s -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -H 'Accept-Language: de' \
  -d '{"text":"Sucuk Gazi 400 g 4,49 €"}' localhost:8080/api/v1/merchant/markets/$MARKET/product-drafts
```
```json
{"engine":"RULES",
 "product":{"name":{"de":"Sucuk Gazi"},"description":{},"keywords":[],"brand":null,"unitSize":"400 g",
   "netQuantity":0.4,"netUnit":"kg","categoryId":"e717949d-a6bf-5996-b710-2be121395249","cuisineCodes":[],
   "organic":false,"allergens":[],"imageUrl":null,"barcode":null},
 "price":{"amountCents":449,"currency":"EUR"},
 "catalogMatches":[{"id":"2cf10041-f61a-5063-b8b4-b31c1cb0d69b","name":"Sucuk","brand":"Bereket Hof","unitSize":"400 g", "…":"…"}],
 "warnings":["AI_NOT_CONFIGURED"]}
```
With `DUKKAN_ANTHROPIC_API_KEY` set, `engine` is `AI`: the photo is read too, names and descriptions
come in de/en/ar, plus brand, cuisines, allergens and keywords. If the model fails or declines, the
rules answer with `AI_UNAVAILABLE` / `AI_DECLINED`. Field-level warnings (`NAME_MISSING`,
`CATEGORY_UNCERTAIN`, `UNIT_SIZE_MISSING`, `PRICE_MISSING`) list what the merchant still has to fill.
Errors: 400 `VALIDATION_FAILED` (neither `text` nor `imageKey`, text > 1000, unknown `imageKey`),
403/404 (not a merchant / not the owner), 429 `RATE_LIMITED` (30 per merchant and hour).

### Errors
Every error is `application/problem+json` with a stable `code`. Validation
errors carry a `code` per field, and clients show their own translation:
```json
{"type":"about:blank","title":"Validation failed","status":400,"code":"VALIDATION_FAILED",
 "errors":[{"field":"lat","code":"DecimalMax","message":"must be less than or equal to 90"}]}
```
The codes are listed in the contract (`components.responses`). Every response
carries an `X-Request-Id`.
