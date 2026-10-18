# Features: discovery (Phase A) and functional specification (Phase C)

Status labels are defined in [technical-analysis.md](technical-analysis.md).
Evidence IDs refer to [evidence.md](evidence.md).

## Phase A: Application discovery

| # | Capability | Status | What is known | Evidence | Needs verification |
|---|---|---|---|---|---|
| A-01 | Screens and navigation | Reported (partial) | Store-like navigation, server-driven pages | S-05, V-02 | Full screen inventory and tab structure |
| A-02 | Registration and login | Unknown | No source describes the steps. A waitlist exists for areas not yet served | S-01 | Required fields; social login or e-mail login |
| A-03 | Categories and browsing | Reported | Full range: fresh produce, bakery, meat, drugstore items | S-04 (dealdoktor), S-06 | Category depth |
| A-04 | Search and filtering | Unknown | Not described in the sources we found | none | Filters, sort options, suggestions |
| A-05 | Product details | Unknown | Not described | none | Fields shown (allergens, Grundpreis, origin) |
| A-06 | Cart | Reported | Called a "shopping list" (Einkaufsliste) that is built up and then scheduled | V-06 | Edits after ordering |
| A-07 | Promotions | Reported | Weekly "Superdeals" and "Vorteilsrezepte" | V-06, S-02 | Promotion mechanics (percent, multi-buy) |
| A-08 | Recipes to ingredients | Reported | All ingredients of a recipe can be added to the list with one click | S-02, V-06 | Portion scaling, substitutions |
| A-09 | Address and delivery area | Reported | The app shows whether delivery is available. A waitlist exists for areas not yet served | V-06, S-01 | Exact flow and when the address is captured |
| A-10 | Delivery slot selection | Reported | The customer picks a day or slot. On delivery day a 20-minute window is communicated | S-01, S-04 | Slot granularity and fee tiers |
| A-11 | Checkout and order lifecycle | Reported (partial) | Cashless in-app payment. Order cutoff 22:00 for next day (2019 figure). Minimum order: sources conflict (€25 / €35 / €45) | S-01, S-04 | Payment methods, edits, cancellation |
| A-12 | Delivery tracking | Reported | Live map or "radar" of the van's route and ETA | S-01, S-03 | Update frequency |
| A-13 | Notifications | Unknown | none | none | Push types |
| A-14 | Profile and settings | Unknown | none | none | none |
| A-15 | Error, loading and offline states | Inferred | Local SQLite caching suggests partial offline reads | V-04 | Actual offline UX |

**Conclusion:** most of the detail needed for a spec is *Unknown*. The
specification below is therefore **our own independent design**. It is
guided by the reported, high-level capabilities above, and it does not
reproduce Picnic behaviour. Where we chose a value for a rule (for example, a
€35 minimum order), the choice is ours and is marked "design choice".

---

## Phase C: Functional specification (MVP)

Complexity scale: **S** ≤ 2 days, **M** 3–5 days, **L** > 5 days (one engineer, backend and mobile together).
API references point to [`packages/shared-contracts/openapi.yaml`](../../packages/shared-contracts/openapi.yaml).

### F-01 Product catalog
- **Description:** browse categories and the products in each category.
- **User story:** As a shopper, I want to browse products by category, so that I can fill my weekly shop.
- **Preconditions:** none (the catalog is public).
- **Interaction:** Home → category list → category → paginated product list → product.
- **Business rules:** categories form a tree through `parentId` and are ordered by `sortOrder`. Unavailable products are shown with `available=false` and cannot be added. Prices come only from the server.
- **Edge cases:** an empty category shows an empty state. A deleted category returns 404.
- **Data:** Category, Product, ProductPrice (history; the current price is the row with no end date).
- **API:** `GET /categories`, `GET /products?categoryId=&page=&size=`.
- **Acceptance criteria:** (1) categories appear in `sortOrder`; (2) a category view pages through all of its products; (3) each tile shows name, unit size, price and promotion badge; (4) loading, error with retry, and empty states are present.
- **Complexity:** M. **Evidence:** A-03 (design choice beyond that).

### F-02 Product search
- **User story:** As a shopper, I want to search by name or brand, so that I can find items quickly.
- **Interaction:** Search tab → typing (debounced 300 ms) → suggestions → results; optional sort and a promotions-only filter.
- **Business rules:** case-insensitive substring match on name, brand and tags. Suggestions need at least 2 characters and return at most 10 results. Sort orders: relevance, price ascending or descending, name.
- **Edge cases:** no results shows an empty state with a hint. Whitespace-only queries are ignored. Queries over 100 characters are rejected with 400.
- **API:** `GET /products?q=&sort=&promotedOnly=`, `GET /search/suggestions?q=`.
- **Acceptance criteria:** searching "milch" finds products whose name contains "Milch" in any letter case; sorting changes the order; the filter limits results to promoted products.
- **Complexity:** S. **Evidence:** none (A-04 Unknown, design choice).

### F-03 Product details
- **User story:** As a shopper, I want to see price, unit price, ingredients and allergens, so that I can decide what to buy.
- **Business rules:** the comparison price (Grundpreis, per kg/l/piece) is shown when it is known. The stepper is limited to `maxOrderQuantity`.
- **Edge cases:** unknown id returns 404 and a "not found" state. An unavailable product disables "add".
- **API:** `GET /products/{id}`.
- **Acceptance criteria:** all fields shown; the stepper cannot go past the maximum; the prices are announced correctly by screen readers.
- **Complexity:** S. **Evidence:** none (A-05 Unknown, design choice). Comparison price is a German legal norm (PAngV), not something we found out about Picnic.

### F-04 Shopping cart
- **User story:** As a shopper, I want to add, change and remove items and see the totals, so that I know what I will pay.
- **Preconditions:** signed in.
- **Interaction:** a stepper on a tile, the detail screen or the cart → `PUT /cart/items/{id}` with the absolute quantity → the server returns the full cart.
- **Business rules:** quantity can be 0–`maxOrderQuantity`, and 0 removes the line. Setting the quantity is idempotent. Totals are computed on the server: subtotal, discount, delivery fee (from the reserved slot) and total. The minimum order value is 3,500 cents after discounts, excluding the fee (design choice; sources conflict, see A-11).
- **Edge cases:** a product becomes unavailable → 422 `PRODUCT_UNAVAILABLE`. Concurrent edits are handled by optimistic locking, so the last write wins with a retry. Over the maximum → 422 `MAX_QUANTITY_EXCEEDED`.
- **Data:** ShoppingCart, CartItem.
- **API:** `GET/DELETE /cart`, `PUT/DELETE /cart/items/{productId}`.
- **Acceptance criteria:** the totals match the pricing rules in `shared-contracts/README.md`; the cart persists across app restarts and devices; a progress bar for the minimum order is shown.
- **Complexity:** M. **Evidence:** A-06.

### F-05 Promotions
- **User story:** As a shopper, I want to see this week's deals and get them applied automatically.
- **Business rules:** two types. **PERCENT_OFF:** per-unit discount, rounded half-up. **MULTI_BUY:** every complete group of N units costs a fixed price. At most one promotion applies per product; the most recently created one wins. A promotion applies only within `[validFrom, validUntil)`.
- **Edge cases:** a promotion expires while the item is in the cart → the next cart read reprices the item. Multi-buy remainder units are charged at full price.
- **API:** `GET /promotions`, `promotion` badge on products and cart lines.
- **Acceptance criteria:** unit tests cover rounding, remainders and expiry; the badge label matches the type (for example "-20%" or "2 for €3.00").
- **Complexity:** M. **Evidence:** A-07 (mechanics are a design choice).

### F-06 Registration and login
- **User story:** As a new customer, I want to create an account with my e-mail address, so that my cart and orders are saved.
- **Business rules:** the e-mail is unique and case-insensitive. Passwords need 10–128 characters and are hashed with BCrypt. A successful register or login returns a JWT that expires after 1 hour. A login failure is 401 `INVALID_CREDENTIALS` and never says which field was wrong.
- **Edge cases:** a duplicate e-mail → 409 `EMAIL_TAKEN`. An expired token → 401 → the app returns to login.
- **API:** `POST /auth/register`, `POST /auth/login`, `GET /me`.
- **Acceptance criteria:** the token is stored only in the platform keystore or keychain; logout deletes it; protected endpoints reject missing or forged tokens.
- **Complexity:** M. **Evidence:** A-02 Unknown (design choice).
- **Out of scope (backlog):** refresh tokens, password reset, e-mail verification, rate limiting.

### F-07 Delivery address workflow
- **User story:** As a customer, I want to save my address and learn right away whether you deliver there.
- **Business rules:** German addresses only. The postal code must match `^[0-9]{5}$`. Serviceability means the postal code is in `delivery_area`. One address is the default. A non-serviceable address can be saved but cannot be used to reserve a slot (422 `ADDRESS_NOT_SERVICEABLE`).
- **Interaction:** the postal code is pre-checked when the field loses focus (`GET /delivery-areas/check`), then the form is saved.
- **API:** `GET/POST /addresses`, `PUT/DELETE /addresses/{id}`, `GET /delivery-areas/check`.
- **Acceptance criteria:** validation messages appear next to the field; another user's address id returns 404.
- **Complexity:** S. **Evidence:** A-09.
- **Backlog:** a waitlist for areas not yet served (reported in S-01).

### F-08 Delivery slot selection (mock availability)
- **User story:** As a customer, I want to pick a delivery slot that suits me.
- **Business rules (all design choices):** Monday to Saturday, the next 7 days, 1-hour slots from 08:00 to 21:00 (Europe/Berlin). Capacity is 8 per slot per hub. Mock fee tiers are 0, 0.99 and 1.99 EUR. The order cutoff is 22:00 on the previous day. A reservation holds capacity until the cutoff.
- **Edge cases:** a full slot → 409 `SLOT_FULL`. The cutoff has passed → 422 `SLOT_CUTOFF_PASSED`. Choosing a different slot releases the previous one. Two users racing for the last place → exactly one wins (pessimistic lock, covered by an integration test).
- **API:** `GET /delivery-slots?addressId=`, `PUT /cart/delivery`.
- **Acceptance criteria:** slots are grouped by day; unavailable slots are disabled and their state is announced to screen readers; the fee is shown and included in the cart total.
- **Complexity:** M. **Evidence:** A-10. Picnic's reported 20-minute narrowing on delivery day is **not** in the MVP (backlog).

### F-09 Checkout (simulated) and orders
- **User story:** As a customer, I want to place my order and see it in my order history.
- **Business rules:** placing an order needs a non-empty cart, an active reservation that is before its cutoff, the minimum order value, and all products available. Prices, address and slot are copied into the order at that moment. **No payment is taken.** An `Idempotency-Key` makes retries safe. Cancellation is allowed only for `PLACED` orders before the cutoff, and it releases capacity.
- **API:** `POST /orders`, `GET /orders`, `GET /orders/{id}`, `POST /orders/{id}/cancel`.
- **Acceptance criteria:** after placing an order the cart is empty; the order total equals the cart total from before; repeating the call with the same key returns the same order.
- **Complexity:** M. **Evidence:** A-11. Payment, fulfilment and live tracking are planned separately (see the backlog).

## Not in MVP (planned)
Recipes and adding their ingredients to the cart (A-08), live tracking (A-12), push
notifications (A-13), a waitlist, real payments (PSP), refunds, deposit returns,
substitutions, and server-driven home layout.
