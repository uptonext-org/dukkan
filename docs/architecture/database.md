# Database design

The database is PostgreSQL 18.6. Flyway owns the schema:
`services/backend/src/main/resources/db/migration/V1__schema.sql` holds the
schema, and `V2__seed_data.sql` holds the invented seed data. All primary keys
are UUIDs. Unless noted, every table has `created_at` and `updated_at`. Money
columns hold `*_cents bigint` plus a `currency`.

## Entity relationship diagram

```mermaid
erDiagram
  app_user ||--o{ address : owns
  app_user ||--o| cart : has
  app_user ||--o{ customer_order : places
  hub ||--o{ delivery_area : serves
  hub ||--o{ delivery_slot : offers
  category ||--o{ category : parent_of
  category ||--o{ product : contains
  product ||--o{ product_price : priced_by
  promotion ||--o{ promotion_product : targets
  product ||--o{ promotion_product : promoted_in
  cart ||--o{ cart_item : contains
  product ||--o{ cart_item : in
  delivery_slot |o--o{ cart : reserved_by
  address |o--o{ cart : reserved_for
  customer_order ||--|{ order_item : contains
  product ||--o{ order_item : snapshot_of
  delivery_slot ||--o{ customer_order : booked_in
  recipe ||--o{ recipe_ingredient : lists
  product |o--o{ recipe_ingredient : fulfils

  app_user {
    uuid id PK
    varchar email UK "lower-case"
    varchar password_hash "BCrypt"
    varchar first_name
    varchar last_name
  }
  address {
    uuid id PK
    uuid user_id FK
    varchar street
    varchar house_number
    varchar postal_code "5 digits"
    varchar city
    varchar country_code "DE"
    boolean is_default "max one per user"
  }
  hub {
    uuid id PK
    varchar code UK
    varchar name
  }
  delivery_area {
    varchar postal_code PK
    uuid hub_id FK
  }
  category {
    uuid id PK
    uuid parent_id FK
    varchar slug UK
    varchar name
    int sort_order
  }
  product {
    uuid id PK
    uuid category_id FK
    varchar sku UK
    varchar name
    varchar brand
    varchar unit_size
    numeric net_quantity
    varchar net_unit "kg, l, piece"
    text_array allergens
    text_array tags
    int max_order_quantity "1-99"
    boolean available
  }
  product_price {
    uuid id PK
    uuid product_id FK
    bigint amount_cents
    timestamptz valid_from
    timestamptz valid_to "NULL = current, one per product"
  }
  promotion {
    uuid id PK
    varchar type "PERCENT_OFF, MULTI_BUY"
    int percent_off
    int bundle_quantity
    bigint bundle_price_cents
    timestamptz valid_from
    timestamptz valid_until
  }
  promotion_product {
    uuid promotion_id PK
    uuid product_id PK
  }
  delivery_slot {
    uuid id PK
    uuid hub_id FK
    timestamptz starts_at "UK with hub_id"
    timestamptz ends_at
    timestamptz order_cutoff_at
    bigint fee_cents
    int capacity
    int used_capacity "0..capacity"
    bigint version
  }
  cart {
    uuid id PK
    uuid user_id FK "UK"
    uuid reserved_slot_id FK
    uuid reserved_address_id FK "ON DELETE SET NULL"
    timestamptz reservation_expires_at
    bigint version
  }
  cart_item {
    uuid id PK
    uuid cart_id FK
    uuid product_id FK "UK with cart_id"
    int quantity "1-99"
  }
  customer_order {
    uuid id PK
    uuid user_id FK
    varchar order_number UK "DK-yyyyMMdd-NNNNNN"
    varchar status
    timestamptz placed_at
    varchar idempotency_key "UK with user_id"
    bigint total_cents "= subtotal - discount + fee"
    uuid slot_id FK
    varchar address_snapshot "address_* columns"
    bigint version
  }
  order_item {
    uuid id PK
    uuid order_id FK
    int line_no
    uuid product_id FK
    varchar name "snapshot"
    bigint unit_price_cents "snapshot"
    int quantity
    bigint line_total_cents
  }
  recipe {
    uuid id PK
    varchar slug UK
    varchar title
    int servings
  }
  recipe_ingredient {
    uuid id PK
    uuid recipe_id FK
    uuid product_id FK
    varchar description
    int sort_order
  }
```

## Changes to the candidate entities

| Candidate | Result |
|---|---|
| User | `app_user`, because `user` is a reserved word in PostgreSQL |
| Address | `address`. Serviceability is derived through `delivery_area`, not stored |
| Product, Category | As proposed. The category tree uses `parent_id` |
| ProductPrice | `product_price` keeps price history. A partial unique index allows one open-ended row per product |
| Promotion | `promotion` plus a `promotion_product` join table. A CHECK constraint enforces the type-specific columns |
| ShoppingCart / CartItem | `cart` (one per user) and `cart_item`. The slot reservation is stored as columns on `cart` instead of a separate table |
| DeliverySlot | `delivery_slot`. Its capacity counter is guarded by `SELECT … FOR UPDATE` |
| Order / OrderItem | `customer_order` (`order` is a reserved word) and `order_item`. Prices, address and slot times are **snapshots** taken when the order is placed |
| Recipe | `recipe` and `recipe_ingredient`. There is no API yet (backlog B-18) |
| *New:* Hub, DeliveryArea | Needed to map postal codes to slot capacity |

## Integrity rules enforced in the database
- E-mail is stored lower-case and unique.
- Each user has at most one default address (partial unique index).
- Each product has exactly one current price (partial unique index).
- Each user's `idempotency_key` is unique (partial unique index), so a checkout retry cannot create a second order.
- `customer_order.total_cents = subtotal - discount + fee` (CHECK).
- `0 ≤ used_capacity ≤ capacity` (CHECK). This is the last line of defence against overselling a slot.

The seed data holds 9 categories, 42 products, 6 promotions (one of them expired), 3 hubs and 10 postal codes. All of it is invented.
