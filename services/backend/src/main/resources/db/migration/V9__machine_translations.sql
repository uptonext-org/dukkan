-- B-38: translations filled by the machine translator are marked, so the UI can say so and a
-- merchant's own text never gets overwritten.
ALTER TABLE catalog_product_translation ADD COLUMN machine_translated boolean NOT NULL DEFAULT false;
