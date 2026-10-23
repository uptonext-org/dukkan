-- Missing items while packing: the market sets how many units of a line it can hand over.
-- NULL = as ordered. The line's charged amount (the cheaper of pro rata and re-pricing with the
-- promotion that applied) and the order totals are recomputed by the application; original_total_cents keeps what the customer saw when ordering.
ALTER TABLE order_item
    ADD COLUMN fulfilled_quantity integer,
    -- Charged amount of a line handed over with fewer units (NULL = line_total_cents).
    ADD COLUMN charged_total_cents bigint,
    -- The promotion that applied when ordering, so fewer units are re-priced by the same rule.
    ADD COLUMN promo_percent_off integer,
    ADD COLUMN promo_bundle_quantity integer,
    ADD COLUMN promo_bundle_price_cents bigint,
    ADD CONSTRAINT ck_order_item_fulfilled CHECK (fulfilled_quantity IS NULL OR (fulfilled_quantity >= 0 AND fulfilled_quantity <= quantity));

ALTER TABLE customer_order
    ADD COLUMN original_total_cents bigint,
    ADD COLUMN adjusted_at timestamptz;
