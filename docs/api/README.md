# API specification

- **Contract (source of truth):** [`packages/shared-contracts/openapi.yaml`](../../packages/shared-contracts/openapi.yaml)
  (OpenAPI 3.1, linted with `@redocly/cli@2.64.1` with 0 errors).
- **Business rules:** [`packages/shared-contracts/README.md`](../../packages/shared-contracts/README.md)
- **Live docs (local):** http://localhost:8080/swagger-ui.html and http://localhost:8080/v3/api-docs
- **Base URL:** `http://localhost:8080/api/v1` (`10.0.2.2` from the Android emulator)

## Endpoints

| Area | Method and path | Auth |
|---|---|---|
| Auth | `POST /auth/register`, `POST /auth/login`, `GET /me` | public, public, JWT |
| Catalog | `GET /categories`, `GET /products`, `GET /products/{id}` | public |
| Search | `GET /products?q=&sort=&promotedOnly=`, `GET /search/suggestions?q=` | public |
| Promotions | `GET /promotions` | public |
| Cart | `GET /cart`, `DELETE /cart`, `PUT/DELETE /cart/items/{productId}`, `PUT /cart/delivery` | JWT |
| Addresses | `GET/POST /addresses`, `PUT/DELETE /addresses/{id}` | JWT |
| Delivery | `GET /delivery-areas/check?postalCode=` (public), `GET /delivery-slots?addressId=` (JWT) | |
| Orders | `GET/POST /orders`, `GET /orders/{id}`, `POST /orders/{id}/cancel` | JWT |

## Examples

The responses below were captured on 2026-10-18 from a local run (backend jar
with the `local` profile, Compose PostgreSQL 18.6, seed data). Some are
shortened with `…`. The UUIDs of generated rows will differ on your machine.

### Register, then log in
```bash
curl -s -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"correct-horse-42","firstName":"Dana","lastName":"Demo"}' \
  localhost:8080/api/v1/auth/register
# 201 {"accessToken":"eyJ…","tokenType":"Bearer","expiresIn":3600,"user":{…}}
TOKEN=…   # accessToken from the response
```
Registering the same e-mail again, in a different letter case:
```json
409 {"type":"about:blank","title":"Conflict","status":409,"code":"EMAIL_TAKEN",
     "detail":"An account with this email already exists.","instance":"/api/v1/auth/register"}
```
Logging in with a wrong password:
```json
401 {"type":"about:blank","title":"Unauthorized","status":401,"code":"INVALID_CREDENTIALS",
     "detail":"Email or password is incorrect.","instance":"/api/v1/auth/login"}
```

### Search and suggestions
```bash
curl -s 'localhost:8080/api/v1/products?q=milk&size=2'
```
```json
{"items":[{"id":"b9265694-…","sku":"DK-2001","name":"Fresh Whole Milk 3.5%","brand":"Weidenhof",
  "unitSize":"1 l","imageUrl":null,"price":{"amountCents":119,"currency":"EUR"},
  "comparisonPrice":{"price":{"amountCents":119,"currency":"EUR"},"unit":"l"},
  "promotion":null,"available":true}],"page":0,"size":2,"totalItems":1,"totalPages":1}
```
`GET /search/suggestions?q=sp` → `["Sponges","Spaghetti","Sparkling Mineral Water","Baby Spinach"]`
(prefix matches come first).

### Delivery area check
`GET /delivery-areas/check?postalCode=41460` → `{"postalCode":"41460","serviceable":true,"hubName":"Hub Rhein-West"}`

`GET /delivery-areas/check?postalCode=12` returns:
```json
400 {"type":"about:blank","title":"Validation failed","status":400,"code":"VALIDATION_FAILED",
     "errors":[{"field":"postalCode","message":"must be a 5-digit German postal code"}], …}
```

### Cart with a promotion
```bash
curl -s -X PUT -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"quantity":3}' localhost:8080/api/v1/cart/items/e7c9c5f6-a54c-4549-8a73-2cca8599292a
```
```json
{"items":[{"name":"Apples Elstar","unitPrice":{"amountCents":249,"currency":"EUR"},"quantity":3,
  "lineSubtotal":{"amountCents":747,…},"lineDiscount":{"amountCents":150,…},"lineTotal":{"amountCents":597,…},
  "promotion":{"label":"-20%","type":"PERCENT_OFF",…}}],
 "itemCount":3,"subtotal":{"amountCents":747,…},"discountTotal":{"amountCents":150,…}, …,
 "minimumOrderValue":{"amountCents":3500,…},"meetsMinimumOrderValue":false,"delivery":null}
```
That is 20% of 249 = 49.8, rounded half-up to 50 cents per unit, so 150 cents across 3 units.

### Address, slots, reservation
```bash
curl -s -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"label":"Home","street":"Musterstraße","houseNumber":"1","postalCode":"41460","city":"Neuss","isDefault":true}' \
  localhost:8080/api/v1/addresses            # 201 {…,"countryCode":"DE","serviceable":true}
curl -s -H "Authorization: Bearer $TOKEN" "localhost:8080/api/v1/delivery-slots?addressId=$ADDRESS_ID"
```
That returned 78 slots (6 delivery days × 13 hourly windows). The first one:
```json
{"id":"17eca396-…","startsAt":"2026-10-19T08:00:00+02:00","endsAt":"2026-10-19T09:00:00+02:00",
 "orderCutoffAt":"2026-10-18T22:00:00+02:00","fee":{"amountCents":99,"currency":"EUR"},
 "available":true,"remainingCapacity":8}
```
`PUT /cart/delivery {"slotId":"…","addressId":"…"}` returns the cart with
`delivery` set and `deliveryFee` equal to the slot fee.

### Simulated checkout
Placing an order below the minimum:
```json
422 {"code":"MINIMUM_ORDER_NOT_MET","detail":"The minimum order value of 3500 cents (after discounts) is not reached.", …}
```
With 17 items (subtotal 7793, discount 524, fee 99 → total 7368 cents):
```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H 'Idempotency-Key: demo-1' localhost:8080/api/v1/orders
# 201 {"orderNumber":"DK-20261018-000001","status":"PLACED","total":{"amountCents":7368,…}, …}
```
- Repeating the call with the same `Idempotency-Key` returned 201 and **the same order id**.
- Afterwards the cart is empty and `delivery` is `null`.
- `POST /orders/{id}/cancel` returns status `CANCELLED`. Cancelling again returns 422 `ORDER_NOT_CANCELLABLE`.

### Errors
Every error is `application/problem+json` (RFC 9457) with a machine-readable `code`:
`VALIDATION_FAILED`, `UNAUTHORIZED`, `INVALID_CREDENTIALS`, `FORBIDDEN`, `NOT_FOUND`,
`EMAIL_TAKEN`, `SLOT_FULL`, `CONFLICT`, `CONCURRENT_MODIFICATION`, `METHOD_NOT_ALLOWED`,
`PRODUCT_UNAVAILABLE`, `MAX_QUANTITY_EXCEEDED`, `ADDRESS_NOT_SERVICEABLE`, `SLOT_CUTOFF_PASSED`,
`SLOT_NOT_RESERVED`, `CART_EMPTY`, `MINIMUM_ORDER_NOT_MET`, `ORDER_NOT_CANCELLABLE`, `INTERNAL_ERROR`.
Every response carries an `X-Request-Id` header. The server echoes the client's
value or generates one, and logs it as `requestId`.
