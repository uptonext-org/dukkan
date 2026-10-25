-- What a merchant controls about ordering (portal page "Orders & delivery"). NULL in a number column
-- means the platform default (dukkan.fulfilment.*, dukkan.catalog.default-max-order-quantity).
-- Existing markets keep taking orders; new markets start with orders_enabled = false (MarketService).
ALTER TABLE market ADD COLUMN orders_enabled boolean NOT NULL DEFAULT true;
-- No new orders before this instant ("pause"); NULL: not paused.
ALTER TABLE market ADD COLUMN orders_paused_until timestamptz;
-- Closed on these local dates (holiday), both inclusive: no slots on them.
ALTER TABLE market ADD COLUMN closed_from date;
ALTER TABLE market ADD COLUMN closed_until date;
ALTER TABLE market ADD COLUMN delivery_capacity smallint;
ALTER TABLE market ADD COLUMN pickup_capacity smallint;
ALTER TABLE market ADD COLUMN delivery_lead_minutes smallint;
ALTER TABLE market ADD COLUMN pickup_lead_minutes smallint;
ALTER TABLE market ADD COLUMN days_ahead smallint;
-- New orders go straight to ACCEPTED.
ALTER TABLE market ADD COLUMN auto_accept boolean NOT NULL DEFAULT false;
-- How customers pay at handover when the platform payment mode is ON_SITE.
ALTER TABLE market ADD COLUMN payment_methods text[] NOT NULL DEFAULT '{CASH}';
ALTER TABLE market ADD COLUMN default_max_order_quantity smallint;

ALTER TABLE market ADD CONSTRAINT ck_market_closed_range
    CHECK (closed_from IS NULL AND closed_until IS NULL
        OR closed_from IS NOT NULL AND closed_until IS NOT NULL AND closed_until >= closed_from);
ALTER TABLE market ADD CONSTRAINT ck_market_capacities
    CHECK ((delivery_capacity IS NULL OR delivery_capacity BETWEEN 1 AND 99)
       AND (pickup_capacity IS NULL OR pickup_capacity BETWEEN 1 AND 99));
ALTER TABLE market ADD CONSTRAINT ck_market_lead_minutes
    CHECK ((delivery_lead_minutes IS NULL OR delivery_lead_minutes BETWEEN 0 AND 2880)
       AND (pickup_lead_minutes IS NULL OR pickup_lead_minutes BETWEEN 0 AND 2880));
ALTER TABLE market ADD CONSTRAINT ck_market_days_ahead CHECK (days_ahead IS NULL OR days_ahead BETWEEN 1 AND 14);
ALTER TABLE market ADD CONSTRAINT ck_market_default_max_quantity
    CHECK (default_max_order_quantity IS NULL OR default_max_order_quantity BETWEEN 1 AND 99);

-- Delivery times when they differ from the opening hours; no rows: deliveries during opening hours.
CREATE TABLE market_delivery_hours (
    market_id   uuid     NOT NULL REFERENCES market (id) ON DELETE CASCADE,
    day_of_week smallint NOT NULL,
    opens_at    time     NOT NULL,
    closes_at   time     NOT NULL,
    PRIMARY KEY (market_id, day_of_week, opens_at),
    CONSTRAINT ck_market_delivery_hours_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_market_delivery_hours_interval CHECK (closes_at <> opens_at)
);
