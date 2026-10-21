# User flows

The first diagram is our reading of the **reported** high-level Picnic flow
(V-06, S-01, S-04). The rest describe the **Dukkan** marketplace design. They
are kept separate on purpose.

## Reported flow (Picnic, high level, not verified on an artifact)

```mermaid
flowchart LR
  A[Open app] --> B{Area served?}
  B -- no --> W[Join waitlist<br/>S-01]
  B -- yes --> C[Build shopping list<br/>V-06]
  C --> R[Recipes: add all ingredients<br/>S-02]
  R --> C
  C --> D[Choose delivery day/slot<br/>V-06, S-04]
  D --> E[Pay in app<br/>S-04]
  E --> F[Delivery day: 20-min window<br/>S-01]
  F --> G[Live tracking<br/>S-03]
```

## Dukkan: navigation (marketplace)

The navigation is described in [architecture/overview.md › Mobile app structure](../architecture/overview.md#mobile-app-structure).
An earlier single-store version of this page was replaced when Dukkan became a marketplace (ADR 0010).

## Dukkan: checkout sequence (one market)

```mermaid
sequenceDiagram
  actor U as Customer
  participant M as Mobile app
  participant B as Backend
  participant DB as PostgreSQL
  actor H as Merchant
  U->>M: Add product in market A (qty 3)
  M->>B: PUT /markets/{A}/cart/items/{id} {quantity:3}
  B->>DB: upsert cart(user, A) item, price with A's promotions
  B-->>M: Cart of market A (server-priced totals)
  U->>M: Choose pickup or delivery time
  M->>B: GET /markets/{A}/slots?type=PICKUP
  B-->>M: slots (capacity, fee, cutoff)
  M->>B: PUT /markets/{A}/cart/fulfilment {type, slotId, addressId?}
  B->>DB: SELECT slot FOR UPDATE, check capacity / delivery radius, reserve
  B-->>M: Cart incl. reservation and fee
  U->>M: Bestellen
  M->>B: POST /markets/{A}/orders (Idempotency-Key)
  B->>DB: validate, snapshot items/prices/address/slot, clear only A's cart
  B-->>M: 201 Order (PLACED)
  H->>B: POST /merchant/orders/{id}/status ACCEPTED → READY_FOR_PICKUP → COMPLETED
```

## Dukkan: order state machine

```mermaid
stateDiagram-v2
  [*] --> PLACED: customer orders
  PLACED --> ACCEPTED: merchant
  PLACED --> REJECTED: merchant
  PLACED --> CANCELLED: customer, before cutoff
  ACCEPTED --> CANCELLED: customer, before cutoff
  ACCEPTED --> READY_FOR_PICKUP: merchant (pickup)
  ACCEPTED --> OUT_FOR_DELIVERY: merchant (delivery)
  READY_FOR_PICKUP --> COMPLETED: merchant
  OUT_FOR_DELIVERY --> COMPLETED: merchant
  REJECTED --> [*]
  CANCELLED --> [*]
  COMPLETED --> [*]
```
