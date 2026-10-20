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
| Max quantity per line | `maxOrderQuantity` per listing (default 24, hard cap 99) |
| Slots | The next 7 days, Monday to Saturday, during opening hours. 1-hour windows. Capacity 6 for delivery and 10 for pickup per window (defaults) |
| Order cutoff | Delivery: 2 h before the window starts. Pickup: 1 h before |
| Reservation | Holds capacity until the cutoff, or until the cart is cleared or the reservation replaced |
| Cart | One per user per market. Placing an order empties only that market's cart |
| Cancellation (customer) | `PLACED` or `ACCEPTED`, before the cutoff |
| Order status (merchant) | PLACED→ACCEPTED or REJECTED; ACCEPTED→READY_FOR_PICKUP (pickup) or OUT_FOR_DELIVERY (delivery); those → COMPLETED. REJECTED and CANCELLED release capacity |
| PERCENT_OFF | Per unit: `round_half_up(unitPrice * percentOff / 100)` off. `promotionalPrice = unitPrice - discount` |
| MULTI_BUY | Each complete group of `bundleQuantity` units costs `bundlePrice`; remaining units cost full price |
| Promotion stacking | At most one active promotion per listing. The most recently created one wins |
| "New" | A listing created within the last 14 days |
| Frequently ordered | A listing that appears in ≥ 2 of the user's non-cancelled orders in that market. Shown in favourites with reason `FREQUENTLY_ORDERED` |
| Merchant scope | Merchants can only read and change their own markets, listings, promotions and orders (other markets return 404) |

All seed data (markets in Hamburg, products, prices, people) is invented test data.
None of it comes from a real retailer or person.
