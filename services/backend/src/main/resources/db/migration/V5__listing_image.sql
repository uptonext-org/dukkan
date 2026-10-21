-- B-32 image upload: a market's own photo of a listing. NULL falls back to catalog_product.image_url,
-- so a merchant can photograph a shared catalog product without changing it for other markets.
-- Values are absolute URLs, usually of uploaded media (GET /api/v1/media/{sha256}.{jpg|png|webp}).
ALTER TABLE market_product ADD COLUMN image_url varchar(500);
