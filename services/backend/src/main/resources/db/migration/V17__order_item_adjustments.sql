-- Missing items while packing: the market sets how many units of a line it can hand over.
-- NULL = as ordered. The line's charged amount and the order totals are recomputed by the
-- application; original_total_cents keeps what the customer saw when ordering.
ALTER TABLE order_item
    ADD COLUMN fulfilled_quantity integer,
    ADD CONSTRAINT ck_order_item_fulfilled CHECK (fulfilled_quantity IS NULL OR (fulfilled_quantity >= 0 AND fulfilled_quantity <= quantity));

ALTER TABLE customer_order
    ADD COLUMN original_total_cents bigint,
    ADD COLUMN adjusted_at timestamptz;
