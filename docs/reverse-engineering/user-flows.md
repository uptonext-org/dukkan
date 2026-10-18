# User flows

The first diagram is our reading of the **reported** high-level Picnic flow
(V-06, S-01, S-04). The rest describe the **Dukkan** design. They are kept
separate on purpose.

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

## Dukkan: navigation map

```mermaid
flowchart TB
  subgraph Auth stack
    L[Login] <--> RG[Register]
  end
  L -->|token| T
  subgraph T[Main tabs]
    H[Home: categories + deals]
    S[Search]
    C[Cart]
    P[Profile]
  end
  H --> CP[Category products] --> PD[Product detail]
  H --> PR[Promotions] --> PD
  S --> PD
  C --> DS[Delivery slot picker]
  DS -->|no address| AF[Address form]
  C --> OC[Order confirmation]
  P --> AL[Addresses] --> AF
  P --> OL[Orders] --> OD[Order detail / cancel]
```

## Dukkan: checkout sequence

```mermaid
sequenceDiagram
  actor U as Shopper
  participant M as Mobile app
  participant B as Backend
  participant DB as PostgreSQL
  U->>M: Add product (qty 3)
  M->>B: PUT /cart/items/{id} {quantity:3}
  B->>DB: upsert cart_item, load active promotions
  B-->>M: Cart (server-priced totals)
  U->>M: Choose slot
  M->>B: GET /delivery-slots?addressId=
  B-->>M: slots (capacity, fee, cutoff)
  M->>B: PUT /cart/delivery {slotId,addressId}
  B->>DB: SELECT slot FOR UPDATE, check capacity, reserve
  B-->>M: Cart incl. reservation + fee
  U->>M: Place order
  M->>B: POST /orders (Idempotency-Key)
  B->>DB: validate, snapshot items/prices/address, convert reservation, clear cart
  B-->>M: 201 Order (PLACED)
```

## Dukkan: order state machine

```mermaid
stateDiagram-v2
  [*] --> PLACED: POST /orders
  PLACED --> CANCELLED: cancel before cutoff
  PLACED --> OUT_FOR_DELIVERY: (future simulator)
  OUT_FOR_DELIVERY --> DELIVERED: (future simulator)
  CANCELLED --> [*]
  DELIVERED --> [*]
```
