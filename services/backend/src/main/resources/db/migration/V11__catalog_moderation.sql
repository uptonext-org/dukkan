-- B-36a: admins review products that merchants create. PENDING until reviewed; PROMOTED products
-- move to the shared catalog (owner_market_id NULL), KEPT_PRIVATE ones stay with their market.
-- NULL for the curated shared catalog.
ALTER TABLE catalog_product ADD COLUMN review_status varchar(20);
ALTER TABLE catalog_product ADD COLUMN reviewed_at timestamptz;
ALTER TABLE catalog_product ADD CONSTRAINT ck_catalog_product_review_status
    CHECK (review_status IS NULL OR review_status IN ('PENDING', 'KEPT_PRIVATE', 'PROMOTED'));
UPDATE catalog_product SET review_status = 'PENDING' WHERE owner_market_id IS NOT NULL;
CREATE INDEX ix_catalog_product_review ON catalog_product (review_status, created_at) WHERE review_status IS NOT NULL;
CREATE INDEX ix_catalog_product_barcode ON catalog_product (barcode) WHERE barcode IS NOT NULL;
