-- B-40: a closing time earlier than the opening time means the interval ends on the next day
-- (18:00–02:00, 20:00–00:00). Only equal times stay invalid; overlaps are checked by the API.
ALTER TABLE market_opening_hours DROP CONSTRAINT ck_market_opening_hours_interval;
ALTER TABLE market_opening_hours ADD CONSTRAINT ck_market_opening_hours_interval CHECK (closes_at <> opens_at);
