-- Promotions v2 (merchant points 4–6):
--   FIXED_PRICE: an offer price per product (promotion_product.offer_price_cents), e.g. 5 € → 2 €.
--   MULTI_BUY with several tiers (promotion_tier), e.g. 3 for 5 € and 7 for 10 €; the cart takes
--     the cheapest combination. bundle_quantity/bundle_price_cents keep the smallest tier.
--   A schedule on top of valid_from/valid_until: weekdays (ISO 1 = Monday … 7) and a daily time
--     window in Berlin time. NULL: every day / all day.
ALTER TABLE promotion DROP CONSTRAINT ck_promotion_type;
ALTER TABLE promotion ADD CONSTRAINT ck_promotion_type CHECK (type IN ('PERCENT_OFF', 'MULTI_BUY', 'FIXED_PRICE'));
ALTER TABLE promotion ADD CONSTRAINT ck_promotion_fixed
    CHECK (type <> 'FIXED_PRICE' OR (percent_off IS NULL AND bundle_quantity IS NULL AND bundle_price_cents IS NULL));
ALTER TABLE promotion DROP CONSTRAINT ck_promotion_bundle;
ALTER TABLE promotion ADD CONSTRAINT ck_promotion_bundle
    CHECK (type <> 'MULTI_BUY' OR (bundle_quantity BETWEEN 2 AND 99 AND bundle_price_cents >= 0 AND percent_off IS NULL));

ALTER TABLE promotion ADD COLUMN weekdays smallint[];
ALTER TABLE promotion ADD COLUMN daily_from time;
ALTER TABLE promotion ADD COLUMN daily_until time;
ALTER TABLE promotion ADD CONSTRAINT ck_promotion_weekdays
    CHECK (weekdays IS NULL OR (cardinality(weekdays) BETWEEN 1 AND 7 AND weekdays <@ ARRAY[1, 2, 3, 4, 5, 6, 7]::smallint[]));
ALTER TABLE promotion ADD CONSTRAINT ck_promotion_daily
    CHECK (daily_from IS NULL AND daily_until IS NULL OR daily_from IS NOT NULL AND daily_until IS NOT NULL AND daily_until > daily_from);

CREATE TABLE promotion_tier (
    promotion_id uuid     NOT NULL REFERENCES promotion (id) ON DELETE CASCADE,
    quantity     smallint NOT NULL,
    price_cents  bigint   NOT NULL,
    PRIMARY KEY (promotion_id, quantity),
    CONSTRAINT ck_promotion_tier CHECK (quantity BETWEEN 2 AND 99 AND price_cents >= 0)
);
INSERT INTO promotion_tier (promotion_id, quantity, price_cents)
SELECT id, bundle_quantity, bundle_price_cents FROM promotion WHERE type = 'MULTI_BUY';

ALTER TABLE promotion_product ADD COLUMN offer_price_cents bigint;
ALTER TABLE promotion_product ADD CONSTRAINT ck_promotion_product_offer CHECK (offer_price_cents IS NULL OR offer_price_cents >= 0);

-- The one definition of "running now", used by every query that looks for active promotions.
CREATE FUNCTION promotion_running(valid_from timestamptz, valid_until timestamptz, weekdays smallint[],
                                  daily_from time, daily_until time, now timestamptz)
    RETURNS boolean LANGUAGE sql STABLE AS $$
    SELECT valid_from <= now AND valid_until > now
       AND (weekdays IS NULL OR extract(isodow FROM now AT TIME ZONE 'Europe/Berlin')::smallint = ANY (weekdays))
       AND (daily_from IS NULL OR ((now AT TIME ZONE 'Europe/Berlin')::time >= daily_from
                                   AND (now AT TIME ZONE 'Europe/Berlin')::time < daily_until))
$$;

-- What an order line keeps of the promotion that applied (re-pricing fewer units, F7).
ALTER TABLE order_item ADD COLUMN promo_offer_price_cents bigint;
-- "3:500;7:1000": all tiers of a multi-buy.
ALTER TABLE order_item ADD COLUMN promo_tiers varchar(200);

INSERT INTO ui_text (key, locale, text) VALUES
    ('badge.FIXED_PRICE', 'de', 'Angebot {price}'),
    ('badge.FIXED_PRICE', 'en', 'Offer {price}'),
    ('badge.FIXED_PRICE', 'ar', 'عرض {price}');
