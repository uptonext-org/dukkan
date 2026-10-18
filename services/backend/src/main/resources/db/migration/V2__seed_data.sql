-- Invented test data only. No product, brand, hub or promotion comes from a real retailer.

INSERT INTO hub (id, code, name, created_at, updated_at) VALUES
    ('7b1f6c2e-0000-4000-8000-000000000001', 'RHEIN-WEST', 'Hub Rhein-West', now(), now()),
    ('7b1f6c2e-0000-4000-8000-000000000002', 'RHEIN-NORD', 'Hub Rhein-Nord', now(), now()),
    ('7b1f6c2e-0000-4000-8000-000000000003', 'DOM-SUED',   'Hub Dom-Süd',    now(), now());

INSERT INTO delivery_area (postal_code, hub_id, created_at, updated_at) VALUES
    ('41460', '7b1f6c2e-0000-4000-8000-000000000001', now(), now()),
    ('41462', '7b1f6c2e-0000-4000-8000-000000000001', now(), now()),
    ('41464', '7b1f6c2e-0000-4000-8000-000000000001', now(), now()),
    ('41061', '7b1f6c2e-0000-4000-8000-000000000001', now(), now()),
    ('41063', '7b1f6c2e-0000-4000-8000-000000000001', now(), now()),
    ('40545', '7b1f6c2e-0000-4000-8000-000000000002', now(), now()),
    ('40547', '7b1f6c2e-0000-4000-8000-000000000002', now(), now()),
    ('40549', '7b1f6c2e-0000-4000-8000-000000000002', now(), now()),
    ('50667', '7b1f6c2e-0000-4000-8000-000000000003', now(), now()),
    ('50668', '7b1f6c2e-0000-4000-8000-000000000003', now(), now());

INSERT INTO category (id, parent_id, slug, name, icon_key, sort_order, created_at, updated_at) VALUES
    ('c0000000-0000-4000-8000-000000000001', NULL, 'fruit-vegetables', 'Fruit & Vegetables', 'leaf',   10, now(), now()),
    ('c0000000-0000-4000-8000-000000000002', 'c0000000-0000-4000-8000-000000000001', 'fruit', 'Fruit', 'apple', 11, now(), now()),
    ('c0000000-0000-4000-8000-000000000003', 'c0000000-0000-4000-8000-000000000001', 'vegetables', 'Vegetables', 'carrot', 12, now(), now()),
    ('c0000000-0000-4000-8000-000000000004', NULL, 'dairy-eggs', 'Dairy & Eggs', 'milk', 20, now(), now()),
    ('c0000000-0000-4000-8000-000000000005', NULL, 'bakery', 'Bakery', 'bread', 30, now(), now()),
    ('c0000000-0000-4000-8000-000000000006', NULL, 'pantry', 'Pantry', 'jar', 40, now(), now()),
    ('c0000000-0000-4000-8000-000000000007', 'c0000000-0000-4000-8000-000000000006', 'pasta-rice', 'Pasta & Rice', 'bowl', 41, now(), now()),
    ('c0000000-0000-4000-8000-000000000008', NULL, 'drinks', 'Drinks', 'bottle', 50, now(), now()),
    ('c0000000-0000-4000-8000-000000000009', NULL, 'household', 'Household', 'sponge', 60, now(), now());

-- Products: (sku, category slug, name, brand, unit size, net qty, net unit, price cents, tags, allergens, origin, available)
CREATE TEMPORARY TABLE seed_product (
    sku text, category_slug text, name text, brand text, unit_size text, net_quantity numeric, net_unit text,
    price_cents bigint, tags text[], allergens text[], origin text, available boolean, description text, ingredients text
) ON COMMIT DROP;

INSERT INTO seed_product VALUES
    ('DK-1001', 'fruit', 'Apples Elstar', 'Feldgut', '1 kg', 1.000, 'kg', 249, '{apple,fruit,regional}', '{}', 'Germany', true, 'Crisp, sweet-sour apples.', NULL),
    ('DK-1002', 'fruit', 'Bananas', NULL, '5 pieces', 5, 'piece', 199, '{banana,fruit}', '{}', 'Ecuador', true, 'Ripe yellow bananas.', NULL),
    ('DK-1003', 'fruit', 'Strawberries', 'Feldgut', '500 g', 0.500, 'kg', 349, '{berries,fruit,seasonal}', '{}', 'Germany', true, NULL, NULL),
    ('DK-1004', 'fruit', 'Lemons', NULL, '500 g', 0.500, 'kg', 179, '{citrus,fruit}', '{}', 'Spain', true, NULL, NULL),
    ('DK-1005', 'fruit', 'Blueberries', 'Morgentau', '250 g', 0.250, 'kg', 299, '{berries,fruit}', '{}', 'Poland', true, NULL, NULL),
    ('DK-1006', 'fruit', 'Oranges for Juicing', NULL, '2 kg', 2.000, 'kg', 399, '{citrus,fruit,juice}', '{}', 'Spain', false, NULL, NULL),
    ('DK-1101', 'vegetables', 'Carrots', 'Feldgut', '1 kg', 1.000, 'kg', 129, '{carrot,vegetable,regional}', '{}', 'Germany', true, NULL, NULL),
    ('DK-1102', 'vegetables', 'Vine Tomatoes', 'Morgentau', '500 g', 0.500, 'kg', 279, '{tomato,vegetable}', '{}', 'Netherlands', true, NULL, NULL),
    ('DK-1103', 'vegetables', 'Cucumber', NULL, '1 piece', 1, 'piece', 69, '{cucumber,vegetable,salad}', '{}', 'Germany', true, NULL, NULL),
    ('DK-1104', 'vegetables', 'Potatoes, waxy', 'Feldgut', '2.5 kg', 2.500, 'kg', 349, '{potato,vegetable,regional}', '{}', 'Germany', true, NULL, NULL),
    ('DK-1105', 'vegetables', 'Red Onions', NULL, '1 kg', 1.000, 'kg', 159, '{onion,vegetable}', '{}', 'Germany', true, NULL, NULL),
    ('DK-1106', 'vegetables', 'Baby Spinach', 'Morgentau', '150 g', 0.150, 'kg', 199, '{spinach,salad,vegetable}', '{}', 'Italy', true, NULL, NULL),
    ('DK-1107', 'vegetables', 'Bell Peppers, mixed', NULL, '500 g', 0.500, 'kg', 299, '{pepper,vegetable}', '{}', 'Spain', true, NULL, NULL),
    ('DK-2001', 'dairy-eggs', 'Fresh Whole Milk 3.5%', 'Weidenhof', '1 l', 1.000, 'l', 119, '{milk,dairy}', '{milk}', 'Germany', true, 'Pasteurised whole milk.', 'Milk'),
    ('DK-2002', 'dairy-eggs', 'Natural Yoghurt 3.8%', 'Weidenhof', '500 g', 0.500, 'kg', 89, '{yoghurt,dairy}', '{milk}', 'Germany', true, NULL, 'Milk, yoghurt cultures'),
    ('DK-2003', 'dairy-eggs', 'Free-Range Eggs', 'Hühnerglück', '10 pieces', 10, 'piece', 329, '{eggs,free-range}', '{egg}', 'Germany', true, NULL, NULL),
    ('DK-2004', 'dairy-eggs', 'Butter, unsalted', 'Weidenhof', '250 g', 0.250, 'kg', 239, '{butter,dairy}', '{milk}', 'Germany', true, NULL, 'Cream'),
    ('DK-2005', 'dairy-eggs', 'Mountain Cheese, sliced', 'Almblick', '150 g', 0.150, 'kg', 279, '{cheese,dairy}', '{milk}', 'Austria', true, NULL, 'Milk, salt, rennet, cultures'),
    ('DK-2006', 'dairy-eggs', 'Oat Drink', 'Haferwerk', '1 l', 1.000, 'l', 179, '{oat,vegan,plant-based}', '{gluten}', 'Germany', true, NULL, 'Water, oats, rapeseed oil, salt'),
    ('DK-2007', 'dairy-eggs', 'Quark, low fat', 'Weidenhof', '500 g', 0.500, 'kg', 109, '{quark,dairy}', '{milk}', 'Germany', true, NULL, NULL),
    ('DK-3001', 'bakery', 'Sourdough Rye Bread', 'Backstube Lindner', '750 g', 0.750, 'kg', 329, '{bread,rye,sourdough}', '{gluten}', 'Germany', true, NULL, 'Rye flour, water, sourdough, salt'),
    ('DK-3002', 'bakery', 'Whole Wheat Rolls', 'Backstube Lindner', '6 pieces', 6, 'piece', 219, '{rolls,bread,breakfast}', '{gluten}', 'Germany', true, NULL, NULL),
    ('DK-3003', 'bakery', 'Butter Croissants', NULL, '4 pieces', 4, 'piece', 249, '{croissant,breakfast}', '{gluten,milk}', 'France', true, NULL, NULL),
    ('DK-3004', 'bakery', 'Toast Bread', 'Backstube Lindner', '500 g', 0.500, 'kg', 149, '{toast,bread}', '{gluten}', 'Germany', true, NULL, NULL),
    ('DK-4001', 'pasta-rice', 'Spaghetti', 'Pastaficio Sole', '500 g', 0.500, 'kg', 129, '{pasta,spaghetti}', '{gluten}', 'Italy', true, NULL, 'Durum wheat semolina'),
    ('DK-4002', 'pasta-rice', 'Penne Rigate', 'Pastaficio Sole', '500 g', 0.500, 'kg', 129, '{pasta,penne}', '{gluten}', 'Italy', true, NULL, 'Durum wheat semolina'),
    ('DK-4003', 'pasta-rice', 'Basmati Rice', 'Reisfeld', '1 kg', 1.000, 'kg', 299, '{rice,basmati}', '{}', 'India', true, NULL, NULL),
    ('DK-4004', 'pasta-rice', 'Red Lentils', 'Reisfeld', '500 g', 0.500, 'kg', 189, '{lentils,legumes,vegan}', '{}', 'Turkey', true, NULL, NULL),
    ('DK-4101', 'pantry', 'Chopped Tomatoes', 'Morgentau', '400 g', 0.400, 'kg', 89, '{tomato,canned}', '{}', 'Italy', true, NULL, NULL),
    ('DK-4102', 'pantry', 'Extra Virgin Olive Oil', 'Olivenhain', '500 ml', 0.500, 'l', 699, '{oil,olive}', '{}', 'Greece', true, NULL, NULL),
    ('DK-4103', 'pantry', 'Wildflower Honey', 'Imkerei Sonnental', '500 g', 0.500, 'kg', 549, '{honey,breakfast}', '{}', 'Germany', true, NULL, NULL),
    ('DK-4104', 'pantry', 'Rolled Oats', 'Haferwerk', '500 g', 0.500, 'kg', 99, '{oats,breakfast,vegan}', '{gluten}', 'Germany', true, NULL, NULL),
    ('DK-4105', 'pantry', 'Chickpeas', 'Morgentau', '400 g', 0.400, 'kg', 99, '{chickpeas,legumes,canned,vegan}', '{}', 'Italy', true, NULL, NULL),
    ('DK-4106', 'pantry', 'Ground Coffee, medium roast', 'Röstwerk Nord', '500 g', 0.500, 'kg', 649, '{coffee}', '{}', NULL, true, NULL, NULL),
    ('DK-5001', 'drinks', 'Sparkling Mineral Water', 'Quellwald', '6 x 1.5 l', 9.000, 'l', 299, '{water,sparkling}', '{}', 'Germany', true, NULL, NULL),
    ('DK-5002', 'drinks', 'Apple Juice, cloudy', 'Feldgut', '1 l', 1.000, 'l', 189, '{juice,apple}', '{}', 'Germany', true, NULL, NULL),
    ('DK-5003', 'drinks', 'Still Mineral Water', 'Quellwald', '6 x 1.5 l', 9.000, 'l', 279, '{water,still}', '{}', 'Germany', true, NULL, NULL),
    ('DK-5004', 'drinks', 'Green Tea', 'Teegarten', '20 bags', 20, 'piece', 229, '{tea}', '{}', NULL, true, NULL, NULL),
    ('DK-6001', 'household', 'Dish Soap', 'Glanzwerk', '500 ml', 0.500, 'l', 149, '{cleaning,dishes}', '{}', NULL, true, NULL, NULL),
    ('DK-6002', 'household', 'Kitchen Towels', 'Glanzwerk', '4 rolls', 4, 'piece', 299, '{paper,kitchen}', '{}', NULL, true, NULL, NULL),
    ('DK-6003', 'household', 'Laundry Detergent', 'Glanzwerk', '1.5 l', 1.500, 'l', 599, '{laundry,cleaning}', '{}', NULL, true, NULL, NULL),
    ('DK-6004', 'household', 'Sponges', NULL, '5 pieces', 5, 'piece', 119, '{cleaning,sponge}', '{}', NULL, true, NULL, NULL);

INSERT INTO product (id, category_id, sku, name, brand, unit_size, net_quantity, net_unit, image_url, description,
                     ingredients, allergens, tags, origin, max_order_quantity, available, created_at, updated_at)
SELECT gen_random_uuid(), c.id, s.sku, s.name, s.brand, s.unit_size, s.net_quantity, s.net_unit, NULL, s.description,
       s.ingredients, s.allergens, s.tags, s.origin, 24, s.available, now(), now()
FROM seed_product s
JOIN category c ON c.slug = s.category_slug;

-- Current prices (open-ended).
INSERT INTO product_price (id, product_id, amount_cents, currency, valid_from, valid_to, created_at, updated_at)
SELECT gen_random_uuid(), p.id, s.price_cents, 'EUR', TIMESTAMPTZ '2026-01-01 00:00:00+01', NULL, now(), now()
FROM seed_product s
JOIN product p ON p.sku = s.sku;

-- A little price history for a few products.
INSERT INTO product_price (id, product_id, amount_cents, currency, valid_from, valid_to, created_at, updated_at)
SELECT gen_random_uuid(), p.id, h.amount_cents, 'EUR', TIMESTAMPTZ '2025-06-01 00:00:00+02', TIMESTAMPTZ '2026-01-01 00:00:00+01', now(), now()
FROM (VALUES ('DK-2001', 109), ('DK-4106', 599), ('DK-2004', 219)) AS h(sku, amount_cents)
JOIN product p ON p.sku = h.sku;

-- Promotions, valid from 2026-01-01 until the end of 2030. created_at decides which one wins on overlap.
INSERT INTO promotion (id, title, description, type, percent_off, bundle_quantity, bundle_price_cents,
                       valid_from, valid_until, created_at, updated_at) VALUES
    ('a0000000-0000-4000-8000-000000000001', 'Fresh fruit week', '20% off selected fruit.', 'PERCENT_OFF', 20, NULL, NULL,
     TIMESTAMPTZ '2026-01-01 00:00:00+01', TIMESTAMPTZ '2030-12-31 23:59:59+01', TIMESTAMPTZ '2026-01-01 08:00:00+01', now()),
    ('a0000000-0000-4000-8000-000000000002', 'Pasta night', 'Any 3 packs of pasta for 3.00 EUR.', 'MULTI_BUY', NULL, 3, 300,
     TIMESTAMPTZ '2026-01-01 00:00:00+01', TIMESTAMPTZ '2030-12-31 23:59:59+01', TIMESTAMPTZ '2026-01-01 08:01:00+01', now()),
    ('a0000000-0000-4000-8000-000000000003', 'Breakfast deal', '15% off breakfast favourites.', 'PERCENT_OFF', 15, NULL, NULL,
     TIMESTAMPTZ '2026-01-01 00:00:00+01', TIMESTAMPTZ '2030-12-31 23:59:59+01', TIMESTAMPTZ '2026-01-01 08:02:00+01', now()),
    ('a0000000-0000-4000-8000-000000000004', 'Water crate offer', '2 crates for 5.00 EUR.', 'MULTI_BUY', NULL, 2, 500,
     TIMESTAMPTZ '2026-01-01 00:00:00+01', TIMESTAMPTZ '2030-12-31 23:59:59+01', TIMESTAMPTZ '2026-01-01 08:03:00+01', now()),
    ('a0000000-0000-4000-8000-000000000005', 'Berry special', '30% off berries (overrides fruit week).', 'PERCENT_OFF', 30, NULL, NULL,
     TIMESTAMPTZ '2026-01-01 00:00:00+01', TIMESTAMPTZ '2030-12-31 23:59:59+01', TIMESTAMPTZ '2026-01-01 08:04:00+01', now()),
    ('a0000000-0000-4000-8000-000000000006', 'Expired winter deal', 'No longer active.', 'PERCENT_OFF', 50, NULL, NULL,
     TIMESTAMPTZ '2025-01-01 00:00:00+01', TIMESTAMPTZ '2025-03-01 00:00:00+01', TIMESTAMPTZ '2025-01-01 08:00:00+01', now());

INSERT INTO promotion_product (promotion_id, product_id)
SELECT pr.promotion_id::uuid, p.id
FROM (VALUES
    ('a0000000-0000-4000-8000-000000000001', 'DK-1001'),
    ('a0000000-0000-4000-8000-000000000001', 'DK-1003'),
    ('a0000000-0000-4000-8000-000000000001', 'DK-1005'),
    ('a0000000-0000-4000-8000-000000000001', 'DK-1004'),
    ('a0000000-0000-4000-8000-000000000002', 'DK-4001'),
    ('a0000000-0000-4000-8000-000000000002', 'DK-4002'),
    ('a0000000-0000-4000-8000-000000000003', 'DK-3003'),
    ('a0000000-0000-4000-8000-000000000003', 'DK-4103'),
    ('a0000000-0000-4000-8000-000000000003', 'DK-4104'),
    ('a0000000-0000-4000-8000-000000000004', 'DK-5001'),
    ('a0000000-0000-4000-8000-000000000004', 'DK-5003'),
    ('a0000000-0000-4000-8000-000000000005', 'DK-1003'),
    ('a0000000-0000-4000-8000-000000000005', 'DK-1005'),
    ('a0000000-0000-4000-8000-000000000006', 'DK-2001')
) AS pr(promotion_id, sku)
JOIN product p ON p.sku = pr.sku;
