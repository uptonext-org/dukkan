-- Dukkan marketplace schema (v0.2). UUID primary keys, money as bigint cents, coordinates as
-- WGS84 double precision. Translatable content lives in *_translation tables keyed by locale, so a
-- new language only needs rows in supported_locale and the translation tables.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ------------------------------------------------------------------ i18n

CREATE TABLE supported_locale (
    code        varchar(10) PRIMARY KEY,
    native_name varchar(50) NOT NULL,
    direction   varchar(3)  NOT NULL,
    enabled     boolean     NOT NULL DEFAULT true,
    sort_order  integer     NOT NULL,
    CONSTRAINT ck_supported_locale_code CHECK (code ~ '^[a-z]{2,3}(-[A-Z]{2})?$'),
    CONSTRAINT ck_supported_locale_direction CHECK (direction IN ('ltr', 'rtl'))
);

-- Short server-generated texts (home section titles, promotion badge templates).
CREATE TABLE ui_text (
    key    varchar(100) NOT NULL,
    locale varchar(10)  NOT NULL REFERENCES supported_locale (code),
    text   varchar(200) NOT NULL,
    PRIMARY KEY (key, locale)
);

-- ------------------------------------------------------------------ identity

CREATE TABLE app_user (
    id               uuid PRIMARY KEY,
    email            varchar(254) NOT NULL,
    password_hash    varchar(255) NOT NULL,
    first_name       varchar(100) NOT NULL,
    last_name        varchar(100) NOT NULL,
    preferred_locale varchar(10) REFERENCES supported_locale (code),
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT ck_app_user_email_lower CHECK (email = lower(email))
);

CREATE TABLE app_user_role (
    user_id uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role    varchar(20) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT ck_app_user_role CHECK (role IN ('CUSTOMER', 'MERCHANT', 'ADMIN'))
);

-- ------------------------------------------------------------------ geo

-- Built-in Hamburg gazetteer behind the GeocodingService interface.
CREATE TABLE gazetteer_place (
    id          uuid PRIMARY KEY,
    kind        varchar(12)      NOT NULL,
    street      varchar(120),
    postal_code varchar(5),
    district    varchar(80),
    city        varchar(80)      NOT NULL,
    lat         double precision NOT NULL,
    lng         double precision NOT NULL,
    CONSTRAINT ck_gazetteer_kind CHECK (kind IN ('DISTRICT', 'POSTAL_CODE', 'STREET')),
    CONSTRAINT ck_gazetteer_postal_code CHECK (postal_code IS NULL OR postal_code ~ '^[0-9]{5}$'),
    CONSTRAINT ck_gazetteer_lat CHECK (lat BETWEEN -90 AND 90),
    CONSTRAINT ck_gazetteer_lng CHECK (lng BETWEEN -180 AND 180),
    CONSTRAINT ck_gazetteer_street CHECK (kind <> 'STREET' OR (street IS NOT NULL AND postal_code IS NOT NULL))
);
CREATE INDEX ix_gazetteer_place_lat_lng ON gazetteer_place (lat, lng);

-- ------------------------------------------------------------------ market

CREATE TABLE market_type (
    code       varchar(30) PRIMARY KEY,
    icon_key   varchar(50),
    sort_order integer NOT NULL
);

CREATE TABLE market_type_translation (
    market_type_code varchar(30)  NOT NULL REFERENCES market_type (code) ON DELETE CASCADE,
    locale           varchar(10)  NOT NULL REFERENCES supported_locale (code),
    name             varchar(100) NOT NULL,
    PRIMARY KEY (market_type_code, locale)
);

CREATE TABLE cuisine (
    code       varchar(30) PRIMARY KEY,
    icon_key   varchar(50),
    sort_order integer NOT NULL
);

CREATE TABLE cuisine_translation (
    cuisine_code varchar(30)  NOT NULL REFERENCES cuisine (code) ON DELETE CASCADE,
    locale       varchar(10)  NOT NULL REFERENCES supported_locale (code),
    name         varchar(100) NOT NULL,
    PRIMARY KEY (cuisine_code, locale)
);

CREATE TABLE market (
    id                       uuid PRIMARY KEY,
    slug                     varchar(100)     NOT NULL,
    name                     varchar(80)      NOT NULL,
    type_code                varchar(30)      NOT NULL REFERENCES market_type (code),
    status                   varchar(20)      NOT NULL,
    primary_locale           varchar(10)      NOT NULL REFERENCES supported_locale (code),
    address_line             varchar(200)     NOT NULL,
    lat                      double precision NOT NULL,
    lng                      double precision NOT NULL,
    phone                    varchar(30),
    logo_url                 varchar(500),
    cover_image_url          varchar(500),
    tags                     text[]           NOT NULL DEFAULT '{}',
    delivery                 boolean          NOT NULL,
    pickup                   boolean          NOT NULL,
    delivery_radius_km       double precision,
    delivery_fee_cents       bigint,
    free_delivery_from_cents bigint,
    minimum_order_cents      bigint           NOT NULL,
    currency                 varchar(3)       NOT NULL DEFAULT 'EUR',
    legal_name               varchar(120)     NOT NULL,
    register_info            varchar(120),
    created_at               timestamptz      NOT NULL,
    updated_at               timestamptz      NOT NULL,
    CONSTRAINT uq_market_slug UNIQUE (slug),
    CONSTRAINT ck_market_status CHECK (status IN ('PENDING_REVIEW', 'ACTIVE', 'SUSPENDED')),
    CONSTRAINT ck_market_lat CHECK (lat BETWEEN -90 AND 90),
    CONSTRAINT ck_market_lng CHECK (lng BETWEEN -180 AND 180),
    CONSTRAINT ck_market_fulfilment CHECK (delivery OR pickup),
    CONSTRAINT ck_market_delivery CHECK (NOT delivery OR (delivery_radius_km BETWEEN 0.5 AND 50 AND delivery_fee_cents >= 0)),
    CONSTRAINT ck_market_free_delivery CHECK (free_delivery_from_cents IS NULL OR free_delivery_from_cents >= 0),
    CONSTRAINT ck_market_minimum_order CHECK (minimum_order_cents >= 0),
    CONSTRAINT ck_market_tags CHECK (tags <@ ARRAY['ORGANIC', 'REGIONAL', 'HALAL', 'VEGAN_FRIENDLY']::text[])
);
-- Radius search: bounding-box prefilter on (lat, lng), exact Haversine distance afterwards.
CREATE INDEX ix_market_lat_lng ON market (lat, lng);
CREATE INDEX ix_market_status ON market (status);

CREATE TABLE market_translation (
    market_id   uuid        NOT NULL REFERENCES market (id) ON DELETE CASCADE,
    locale      varchar(10) NOT NULL REFERENCES supported_locale (code),
    description varchar(2000),
    PRIMARY KEY (market_id, locale)
);

CREATE TABLE market_cuisine (
    market_id    uuid        NOT NULL REFERENCES market (id) ON DELETE CASCADE,
    cuisine_code varchar(30) NOT NULL REFERENCES cuisine (code),
    PRIMARY KEY (market_id, cuisine_code)
);
CREATE INDEX ix_market_cuisine_cuisine ON market_cuisine (cuisine_code);

-- Several intervals per day are allowed (e.g. a lunch break). Local time Europe/Berlin.
CREATE TABLE market_opening_hours (
    market_id   uuid     NOT NULL REFERENCES market (id) ON DELETE CASCADE,
    day_of_week smallint NOT NULL,
    opens_at    time     NOT NULL,
    closes_at   time     NOT NULL,
    PRIMARY KEY (market_id, day_of_week, opens_at),
    CONSTRAINT ck_market_opening_hours_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_market_opening_hours_interval CHECK (closes_at > opens_at)
);

CREATE TABLE market_owner (
    market_id  uuid        NOT NULL REFERENCES market (id) ON DELETE CASCADE,
    user_id    uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (market_id, user_id)
);
CREATE INDEX ix_market_owner_user ON market_owner (user_id);

CREATE TABLE banner (
    id          uuid PRIMARY KEY,
    market_id   uuid         NOT NULL REFERENCES market (id) ON DELETE CASCADE,
    tint        varchar(10)  NOT NULL DEFAULT 'sage',
    image_url   varchar(500),
    target_type varchar(20)  NOT NULL,
    target_id   uuid,
    sort_order  integer      NOT NULL,
    active      boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    CONSTRAINT ck_banner_tint CHECK (tint IN ('sage', 'sky', 'blush', 'sand', 'rose')),
    CONSTRAINT ck_banner_target CHECK (target_type IN ('CATEGORY', 'PROMOTIONS', 'NEW', 'PRODUCT')),
    CONSTRAINT ck_banner_target_id CHECK ((target_type IN ('CATEGORY', 'PRODUCT')) = (target_id IS NOT NULL))
);
CREATE INDEX ix_banner_market ON banner (market_id, sort_order);

CREATE TABLE banner_translation (
    banner_id uuid         NOT NULL REFERENCES banner (id) ON DELETE CASCADE,
    locale    varchar(10)  NOT NULL REFERENCES supported_locale (code),
    overline  varchar(100),
    title     varchar(150) NOT NULL,
    PRIMARY KEY (banner_id, locale)
);

-- ------------------------------------------------------------------ catalog

CREATE TABLE category (
    id         uuid PRIMARY KEY,
    parent_id  uuid REFERENCES category (id),
    slug       varchar(80) NOT NULL UNIQUE,
    icon_key   varchar(50),
    image_url  varchar(500),
    sort_order integer     NOT NULL
);
CREATE INDEX ix_category_parent ON category (parent_id);

CREATE TABLE category_translation (
    category_id uuid         NOT NULL REFERENCES category (id) ON DELETE CASCADE,
    locale      varchar(10)  NOT NULL REFERENCES supported_locale (code),
    name        varchar(100) NOT NULL,
    PRIMARY KEY (category_id, locale)
);

-- Shared catalog ("model B"). owner_market_id is NULL for shared master products and set for
-- products a market created itself (visible only to that market's merchant).
-- search_text: normalised names of all translations + brand + keywords (TextNormalizer).
CREATE TABLE catalog_product (
    id              uuid PRIMARY KEY,
    owner_market_id uuid REFERENCES market (id) ON DELETE CASCADE,
    category_id     uuid        NOT NULL REFERENCES category (id),
    brand           varchar(80),
    unit_size       varchar(40) NOT NULL,
    net_quantity    numeric(10, 3),
    net_unit        varchar(5),
    organic         boolean     NOT NULL DEFAULT false,
    allergens       text[]      NOT NULL DEFAULT '{}',
    keywords        text[]      NOT NULL DEFAULT '{}',
    barcode         varchar(20),
    image_url       varchar(500),
    origin          varchar(2),
    search_text     text,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT ck_catalog_product_net_unit CHECK (net_unit IN ('kg', 'l', 'piece')),
    CONSTRAINT ck_catalog_product_net CHECK ((net_quantity IS NULL) = (net_unit IS NULL) AND (net_quantity IS NULL OR net_quantity > 0)),
    CONSTRAINT ck_catalog_product_origin CHECK (origin IS NULL OR origin ~ '^[A-Z]{2}$')
);
CREATE INDEX ix_catalog_product_category ON catalog_product (category_id);
CREATE INDEX ix_catalog_product_owner ON catalog_product (owner_market_id);
CREATE INDEX ix_catalog_product_search ON catalog_product USING gin (search_text gin_trgm_ops);

CREATE TABLE catalog_product_translation (
    catalog_product_id uuid         NOT NULL REFERENCES catalog_product (id) ON DELETE CASCADE,
    locale             varchar(10)  NOT NULL REFERENCES supported_locale (code),
    name               varchar(2000) NOT NULL,
    description        varchar(2000),
    tagline            varchar(2000),
    ingredients        varchar(2000),
    search_name        varchar(2000),
    PRIMARY KEY (catalog_product_id, locale)
);

CREATE TABLE catalog_product_cuisine (
    catalog_product_id uuid        NOT NULL REFERENCES catalog_product (id) ON DELETE CASCADE,
    cuisine_code       varchar(30) NOT NULL REFERENCES cuisine (code),
    PRIMARY KEY (catalog_product_id, cuisine_code)
);
CREATE INDEX ix_catalog_product_cuisine_cuisine ON catalog_product_cuisine (cuisine_code);

-- A market's listing of a catalog product. Its id is the "product id" of the API.
CREATE TABLE market_product (
    id                 uuid PRIMARY KEY,
    market_id          uuid        NOT NULL REFERENCES market (id) ON DELETE CASCADE,
    catalog_product_id uuid        NOT NULL REFERENCES catalog_product (id),
    price_cents        bigint      NOT NULL,
    currency           varchar(3)  NOT NULL DEFAULT 'EUR',
    available          boolean     NOT NULL DEFAULT true,
    max_order_quantity integer     NOT NULL DEFAULT 24,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    deleted_at         timestamptz,
    CONSTRAINT ck_market_product_price CHECK (price_cents >= 0),
    CONSTRAINT ck_market_product_max_qty CHECK (max_order_quantity BETWEEN 1 AND 99)
);
CREATE UNIQUE INDEX uq_market_product_listing ON market_product (market_id, catalog_product_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_market_product_market ON market_product (market_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_market_product_catalog ON market_product (catalog_product_id);

-- Price history of a listing; the current price is the row with valid_to IS NULL and equals
-- market_product.price_cents.
CREATE TABLE market_product_price (
    id                uuid PRIMARY KEY,
    market_product_id uuid        NOT NULL REFERENCES market_product (id) ON DELETE CASCADE,
    amount_cents      bigint      NOT NULL,
    valid_from        timestamptz NOT NULL,
    valid_to          timestamptz,
    CONSTRAINT ck_market_product_price_amount CHECK (amount_cents >= 0),
    CONSTRAINT ck_market_product_price_range CHECK (valid_to IS NULL OR valid_to >= valid_from)
);
CREATE UNIQUE INDEX uq_market_product_price_current ON market_product_price (market_product_id) WHERE valid_to IS NULL;

-- ------------------------------------------------------------------ promotion

CREATE TABLE promotion (
    id                 uuid PRIMARY KEY,
    market_id          uuid        NOT NULL REFERENCES market (id) ON DELETE CASCADE,
    type               varchar(20) NOT NULL,
    percent_off        integer,
    bundle_quantity    integer,
    bundle_price_cents bigint,
    valid_from         timestamptz NOT NULL,
    valid_until        timestamptz NOT NULL,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    CONSTRAINT ck_promotion_type CHECK (type IN ('PERCENT_OFF', 'MULTI_BUY')),
    CONSTRAINT ck_promotion_percent CHECK (type <> 'PERCENT_OFF' OR (percent_off BETWEEN 1 AND 90 AND bundle_quantity IS NULL AND bundle_price_cents IS NULL)),
    CONSTRAINT ck_promotion_bundle CHECK (type <> 'MULTI_BUY' OR (bundle_quantity BETWEEN 2 AND 20 AND bundle_price_cents >= 0 AND percent_off IS NULL)),
    CONSTRAINT ck_promotion_validity CHECK (valid_until >= valid_from)
);
CREATE INDEX ix_promotion_market_validity ON promotion (market_id, valid_from, valid_until);

CREATE TABLE promotion_translation (
    promotion_id uuid         NOT NULL REFERENCES promotion (id) ON DELETE CASCADE,
    locale       varchar(10)  NOT NULL REFERENCES supported_locale (code),
    title        varchar(2000) NOT NULL,
    description  varchar(2000),
    PRIMARY KEY (promotion_id, locale)
);

CREATE TABLE promotion_product (
    promotion_id      uuid NOT NULL REFERENCES promotion (id) ON DELETE CASCADE,
    market_product_id uuid NOT NULL REFERENCES market_product (id) ON DELETE CASCADE,
    PRIMARY KEY (promotion_id, market_product_id)
);
CREATE INDEX ix_promotion_product_listing ON promotion_product (market_product_id);

-- ------------------------------------------------------------------ favorite

CREATE TABLE favorite_market (
    user_id    uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    market_id  uuid        NOT NULL REFERENCES market (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, market_id)
);

CREATE TABLE favorite_product (
    user_id           uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    market_product_id uuid        NOT NULL REFERENCES market_product (id) ON DELETE CASCADE,
    created_at        timestamptz NOT NULL,
    PRIMARY KEY (user_id, market_product_id)
);

-- ------------------------------------------------------------------ address

CREATE TABLE address (
    id                    uuid PRIMARY KEY,
    user_id               uuid             NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    label                 varchar(50),
    street                varchar(120)     NOT NULL,
    house_number          varchar(10)      NOT NULL,
    addition              varchar(50),
    postal_code           varchar(5)       NOT NULL,
    city                  varchar(80)      NOT NULL,
    country_code          varchar(2)       NOT NULL DEFAULT 'DE',
    delivery_instructions varchar(300),
    lat                   double precision,
    lng                   double precision,
    is_default            boolean          NOT NULL DEFAULT false,
    created_at            timestamptz      NOT NULL,
    updated_at            timestamptz      NOT NULL,
    CONSTRAINT ck_address_postal_code CHECK (postal_code ~ '^[0-9]{5}$'),
    CONSTRAINT ck_address_country CHECK (country_code = 'DE'),
    CONSTRAINT ck_address_location CHECK ((lat IS NULL) = (lng IS NULL) AND (lat IS NULL OR (lat BETWEEN -90 AND 90 AND lng BETWEEN -180 AND 180)))
);
CREATE INDEX ix_address_user ON address (user_id);
CREATE UNIQUE INDEX uq_address_one_default_per_user ON address (user_id) WHERE is_default;

-- ------------------------------------------------------------------ fulfilment

-- One-hour delivery or pickup window of a market, generated on demand. used_capacity counts
-- active cart reservations and open orders.
CREATE TABLE fulfilment_slot (
    id              uuid PRIMARY KEY,
    market_id       uuid        NOT NULL REFERENCES market (id) ON DELETE CASCADE,
    type            varchar(10) NOT NULL,
    starts_at       timestamptz NOT NULL,
    ends_at         timestamptz NOT NULL,
    order_cutoff_at timestamptz NOT NULL,
    capacity        integer     NOT NULL,
    used_capacity   integer     NOT NULL DEFAULT 0,
    version         bigint      NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT uq_fulfilment_slot UNIQUE (market_id, type, starts_at),
    CONSTRAINT ck_fulfilment_slot_type CHECK (type IN ('DELIVERY', 'PICKUP')),
    CONSTRAINT ck_fulfilment_slot_capacity CHECK (used_capacity BETWEEN 0 AND capacity),
    CONSTRAINT ck_fulfilment_slot_window CHECK (ends_at > starts_at AND order_cutoff_at <= starts_at)
);

-- ------------------------------------------------------------------ cart

-- One cart per user and market. The reservation (type, slot, address) holds one unit of slot
-- capacity until the cutoff, until the cart is cleared, or until it is replaced.
CREATE TABLE cart (
    id                     uuid PRIMARY KEY,
    user_id                uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    market_id              uuid        NOT NULL REFERENCES market (id) ON DELETE CASCADE,
    fulfilment_type        varchar(10),
    reserved_slot_id       uuid REFERENCES fulfilment_slot (id),
    reserved_address_id    uuid REFERENCES address (id) ON DELETE SET NULL,
    reservation_expires_at timestamptz,
    version                bigint      NOT NULL DEFAULT 0,
    created_at             timestamptz NOT NULL,
    updated_at             timestamptz NOT NULL,
    CONSTRAINT uq_cart_user_market UNIQUE (user_id, market_id),
    CONSTRAINT ck_cart_fulfilment_type CHECK (fulfilment_type IN ('DELIVERY', 'PICKUP')),
    CONSTRAINT ck_cart_reservation CHECK ((reserved_slot_id IS NULL) = (reservation_expires_at IS NULL) AND (reserved_slot_id IS NULL) = (fulfilment_type IS NULL))
);
CREATE INDEX ix_cart_slot ON cart (reserved_slot_id);

CREATE TABLE cart_item (
    id                uuid PRIMARY KEY,
    cart_id           uuid        NOT NULL REFERENCES cart (id) ON DELETE CASCADE,
    market_product_id uuid        NOT NULL REFERENCES market_product (id),
    quantity          integer     NOT NULL,
    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL,
    CONSTRAINT uq_cart_item_listing UNIQUE (cart_id, market_product_id),
    CONSTRAINT ck_cart_item_quantity CHECK (quantity BETWEEN 1 AND 99)
);

-- ------------------------------------------------------------------ order

CREATE SEQUENCE order_number_seq START WITH 1 INCREMENT BY 1;

-- A placed order with snapshots of market name, customer name, prices, slot and address.
CREATE TABLE customer_order (
    id                            uuid PRIMARY KEY,
    user_id                       uuid         NOT NULL REFERENCES app_user (id),
    market_id                     uuid         NOT NULL REFERENCES market (id),
    order_number                  varchar(30)  NOT NULL UNIQUE,
    status                        varchar(20)  NOT NULL,
    fulfilment_type               varchar(10)  NOT NULL,
    placed_at                     timestamptz  NOT NULL,
    idempotency_key               varchar(64),
    market_name                   varchar(80)  NOT NULL,
    customer_name                 varchar(201) NOT NULL,
    subtotal_cents                bigint       NOT NULL,
    discount_total_cents          bigint       NOT NULL,
    delivery_fee_cents            bigint       NOT NULL,
    total_cents                   bigint       NOT NULL,
    currency                      varchar(3)   NOT NULL DEFAULT 'EUR',
    slot_id                       uuid         NOT NULL REFERENCES fulfilment_slot (id),
    slot_starts_at                timestamptz  NOT NULL,
    slot_ends_at                  timestamptz  NOT NULL,
    slot_order_cutoff_at          timestamptz  NOT NULL,
    address_id                    uuid,
    address_label                 varchar(50),
    address_street                varchar(120),
    address_house_number          varchar(10),
    address_addition              varchar(50),
    address_postal_code           varchar(5),
    address_city                  varchar(80),
    address_country_code          varchar(2),
    address_delivery_instructions varchar(300),
    address_lat                   double precision,
    address_lng                   double precision,
    version                       bigint       NOT NULL DEFAULT 0,
    created_at                    timestamptz  NOT NULL,
    updated_at                    timestamptz  NOT NULL,
    CONSTRAINT uq_customer_order_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_customer_order_status CHECK (status IN ('PLACED', 'ACCEPTED', 'REJECTED', 'READY_FOR_PICKUP', 'OUT_FOR_DELIVERY', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_customer_order_type CHECK (fulfilment_type IN ('DELIVERY', 'PICKUP')),
    CONSTRAINT ck_customer_order_address CHECK (fulfilment_type <> 'DELIVERY' OR (address_street IS NOT NULL AND address_postal_code IS NOT NULL)),
    CONSTRAINT ck_customer_order_totals CHECK (total_cents = subtotal_cents - discount_total_cents + delivery_fee_cents)
);
CREATE INDEX ix_customer_order_user ON customer_order (user_id, placed_at DESC);
CREATE INDEX ix_customer_order_market ON customer_order (market_id, placed_at DESC);

CREATE TABLE order_item (
    id                 uuid PRIMARY KEY,
    order_id           uuid         NOT NULL REFERENCES customer_order (id) ON DELETE CASCADE,
    line_no            integer      NOT NULL,
    market_product_id  uuid         NOT NULL,
    catalog_product_id uuid         NOT NULL,
    name               varchar(2000) NOT NULL,
    unit_price_cents   bigint       NOT NULL,
    quantity           integer      NOT NULL,
    line_total_cents   bigint       NOT NULL,
    CONSTRAINT uq_order_item_line UNIQUE (order_id, line_no),
    CONSTRAINT ck_order_item_quantity CHECK (quantity >= 1)
);
CREATE INDEX ix_order_item_listing ON order_item (market_product_id);
