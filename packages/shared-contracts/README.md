# Shared contracts

`openapi.yaml` is the contract-first API definition used by both
`services/backend` and `apps/mobile`. When you change it, update both sides in
the same change. The backend's contract test fails when an operation in this
file has no handler.

## Business rules the contract relies on

Both clients and server must follow these rules. The server is authoritative:
the mobile app only displays what the server returns and never computes prices.

| Rule | Value (MVP default, configurable in the backend) |
|---|---|
| Currency | EUR, amounts in cents |
| Minimum order value | 3 500 cents, after discounts, excluding delivery fee |
| Max quantity per product line | `ProductDetail.maxOrderQuantity` (seed default 24; hard cap 99) |
| Delivery days | Monday to Saturday, next 7 calendar days, timezone `Europe/Berlin` |
| Slot windows | 1 hour, from 08:00 to 21:00 (last slot starts at 20:00) |
| Slot capacity | 8 per slot per hub; consumed by placed orders and active reservations |
| Slot fee | Mock tiers: 0, 99 or 199 cents, depending on the hour |
| Order cutoff | 22:00 on the day before the slot |
| Reservation lifetime | Until the slot's cutoff, or until the cart is cleared or replaced |
| Cancellation | Only `PLACED` orders, only before the slot cutoff |
| Serviceability | A postal code is serviceable if it is in the seeded `delivery_area` table |
| PERCENT_OFF | Each unit is discounted by `round_half_up(unitPrice * percentOff / 100)` |
| MULTI_BUY | Each complete group of `bundleQuantity` units costs `bundlePrice`; any remaining units cost full price |
| Promotion stacking | At most one active promotion per product. If several overlap, the one created most recently wins |

All of the seed data (products, categories, hubs, promotions) is invented test data.
None of it comes from a real retailer.
