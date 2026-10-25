-- A market's own product details for one listing (merchant point 2): the merchant adjusts a shared
-- catalog product when listing it without changing the catalog for other markets. NULL: the catalog
-- value. Texts are in the market's language and shown in every language.
ALTER TABLE market_product ADD COLUMN name varchar(200);
ALTER TABLE market_product ADD COLUMN description varchar(2000);
ALTER TABLE market_product ADD COLUMN unit_size varchar(40);
ALTER TABLE market_product ADD COLUMN brand varchar(80);
-- As catalog_product_translation.search_name / catalog_product.search_text, for the own name.
ALTER TABLE market_product ADD COLUMN search_name varchar(200);
ALTER TABLE market_product ADD COLUMN search_text text;
CREATE INDEX ix_market_product_search ON market_product USING gin (search_text gin_trgm_ops) WHERE search_text IS NOT NULL;
