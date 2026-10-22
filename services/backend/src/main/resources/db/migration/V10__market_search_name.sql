-- B-41: the market name normalised like search terms (TextNormalizer: case, accents, Arabic letter
-- forms), so the name filter runs in SQL. Filled by the API on create/update and at startup for
-- existing rows (MarketSearchIndexer), since the normaliser lives in Java.
ALTER TABLE market ADD COLUMN search_name text;
CREATE INDEX ix_market_search_name ON market USING gin (search_name gin_trgm_ops);
