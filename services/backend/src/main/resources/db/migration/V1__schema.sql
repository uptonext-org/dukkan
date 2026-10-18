-- Dukkan MVP schema. UUID primary keys, audit timestamps, money as bigint cents.

CREATE TABLE app_user (
    id            uuid PRIMARY KEY,
    email         varchar(254) NOT NULL,
    password_hash varchar(255) NOT NULL,
    first_name    varchar(100) NOT NULL,
    last_name     varchar(100) NOT NULL,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT ck_app_user_email_lower CHECK (email = lower(email))
);

CREATE TABLE hub (
    id         uuid PRIMARY KEY,
    code       varchar(30)  NOT NULL UNIQUE,
    name       varchar(100) NOT NULL,
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL
);

CREATE TABLE delivery_area (
    postal_code varchar(5)     PRIMARY KEY,
    hub_id      uuid        NOT NULL REFERENCES hub (id),
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    CONSTRAINT ck_delivery_area_postal_code CHECK (postal_code ~ '^[0-9]{5}$')
);
CREATE INDEX ix_delivery_area_hub ON delivery_area (hub_id);

CREATE TABLE address (
    id                    uuid PRIMARY KEY,
    user_id               uuid         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    label                 varchar(50),
    street                varchar(120) NOT NULL,
    house_number          varchar(10)  NOT NULL,
    addition              varchar(50),
    postal_code           varchar(5)      NOT NULL,
    city                  varchar(80)  NOT NULL,
    country_code          varchar(2)      NOT NULL DEFAULT 'DE',
    delivery_instructions varchar(300),
    is_default            boolean      NOT NULL DEFAULT false,
    created_at            timestamptz  NOT NULL,
    updated_at            timestamptz  NOT NULL,
    CONSTRAINT ck_address_postal_code CHECK (postal_code ~ '^[0-9]{5}$'),
    CONSTRAINT ck_address_country CHECK (country_code = 'DE')
);
CREATE INDEX ix_address_user ON address (user_id);
CREATE UNIQUE INDEX uq_address_one_default_per_user ON address (user_id) WHERE is_default;

CREATE TABLE category (
    id         uuid PRIMARY KEY,
    parent_id  uuid REFERENCES category (id),
    slug       varchar(80)  NOT NULL UNIQUE,
    name       varchar(100) NOT NULL,
    icon_key   varchar(50),
    sort_order integer      NOT NULL,
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL
);
CREATE INDEX ix_category_parent ON category (parent_id);

CREATE TABLE product (
    id                 uuid PRIMARY KEY,
    category_id        uuid          NOT NULL REFERENCES category (id),
    sku                varchar(40)   NOT NULL UNIQUE,
    name               varchar(150)  NOT NULL,
    brand              varchar(80),
    unit_size          varchar(40)   NOT NULL,
    net_quantity       numeric(10, 3),
    net_unit           varchar(5),
    image_url          varchar(500),
    description        text,
    ingredients        text,
    allergens          text[]        NOT NULL DEFAULT '{}',
    tags               text[]        NOT NULL DEFAULT '{}',
    origin             varchar(80),
    max_order_quantity integer       NOT NULL DEFAULT 24,
    available          boolean       NOT NULL DEFAULT true,
    created_at         timestamptz   NOT NULL,
    updated_at         timestamptz   NOT NULL,
    CONSTRAINT ck_product_max_qty CHECK (max_order_quantity BETWEEN 1 AND 99),
    CONSTRAINT ck_product_net_unit CHECK (net_unit IN ('kg', 'l', 'piece')),
    CONSTRAINT ck_product_net CHECK ((net_quantity IS NULL) = (net_unit IS NULL) AND (net_quantity IS NULL OR net_quantity > 0))
);
CREATE INDEX ix_product_category ON product (category_id);
CREATE INDEX ix_product_lower_name ON product (lower(name));

-- Price history: the current price is the row with valid_to IS NULL.
CREATE TABLE product_price (
    id           uuid PRIMARY KEY,
    product_id   uuid        NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    amount_cents bigint      NOT NULL,
    currency     varchar(3)     NOT NULL DEFAULT 'EUR',
    valid_from   timestamptz NOT NULL,
    valid_to     timestamptz,
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    CONSTRAINT ck_product_price_amount CHECK (amount_cents >= 0),
    CONSTRAINT ck_product_price_range CHECK (valid_to IS NULL OR valid_to > valid_from)
);
CREATE UNIQUE INDEX uq_product_price_current ON product_price (product_id) WHERE valid_to IS NULL;
CREATE INDEX ix_product_price_product ON product_price (product_id, valid_from);

CREATE TABLE promotion (
    id                 uuid PRIMARY KEY,
    title              varchar(120) NOT NULL,
    description        varchar(500),
    type               varchar(20)  NOT NULL,
    percent_off        integer,
    bundle_quantity    integer,
    bundle_price_cents bigint,
    valid_from         timestamptz  NOT NULL,
    valid_until        timestamptz  NOT NULL,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    CONSTRAINT ck_promotion_type CHECK (type IN ('PERCENT_OFF', 'MULTI_BUY')),
    CONSTRAINT ck_promotion_percent CHECK (type <> 'PERCENT_OFF' OR (percent_off BETWEEN 1 AND 90 AND bundle_quantity IS NULL AND bundle_price_cents IS NULL)),
    CONSTRAINT ck_promotion_bundle CHECK (type <> 'MULTI_BUY' OR (bundle_quantity >= 2 AND bundle_price_cents >= 0 AND percent_off IS NULL)),
    CONSTRAINT ck_promotion_validity CHECK (valid_until > valid_from)
);
CREATE INDEX ix_promotion_validity ON promotion (valid_from, valid_until);

CREATE TABLE promotion_product (
    promotion_id uuid NOT NULL REFERENCES promotion (id) ON DELETE CASCADE,
    product_id   uuid NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    PRIMARY KEY (promotion_id, product_id)
);
CREATE INDEX ix_promotion_product_product ON promotion_product (product_id);

CREATE TABLE delivery_slot (
    id              uuid PRIMARY KEY,
    hub_id          uuid        NOT NULL REFERENCES hub (id),
    starts_at       timestamptz NOT NULL,
    ends_at         timestamptz NOT NULL,
    order_cutoff_at timestamptz NOT NULL,
    fee_cents       bigint      NOT NULL,
    capacity        integer     NOT NULL,
    used_capacity   integer     NOT NULL DEFAULT 0,
    version         bigint      NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    CONSTRAINT uq_delivery_slot_hub_start UNIQUE (hub_id, starts_at),
    CONSTRAINT ck_delivery_slot_capacity CHECK (used_capacity BETWEEN 0 AND capacity),
    CONSTRAINT ck_delivery_slot_window CHECK (ends_at > starts_at AND order_cutoff_at <= starts_at),
    CONSTRAINT ck_delivery_slot_fee CHECK (fee_cents >= 0)
);

-- One active cart per user. The slot reservation lives on the cart; it holds one unit of
-- delivery_slot.used_capacity until the cart is cleared, replaced or checked out.
CREATE TABLE cart (
    id                     uuid PRIMARY KEY,
    user_id                uuid        NOT NULL UNIQUE REFERENCES app_user (id) ON DELETE CASCADE,
    reserved_slot_id       uuid REFERENCES delivery_slot (id),
    reserved_address_id    uuid REFERENCES address (id) ON DELETE SET NULL,
    reservation_expires_at timestamptz,
    version                bigint      NOT NULL DEFAULT 0,
    created_at             timestamptz NOT NULL,
    updated_at             timestamptz NOT NULL
);

CREATE TABLE cart_item (
    id         uuid PRIMARY KEY,
    cart_id    uuid        NOT NULL REFERENCES cart (id) ON DELETE CASCADE,
    product_id uuid        NOT NULL REFERENCES product (id),
    quantity   integer     NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_cart_item_product UNIQUE (cart_id, product_id),
    CONSTRAINT ck_cart_item_quantity CHECK (quantity BETWEEN 1 AND 99)
);

CREATE SEQUENCE order_number_seq START WITH 1 INCREMENT BY 1;

-- Orders snapshot prices, slot and address so later changes do not alter placed orders.
CREATE TABLE customer_order (
    id                            uuid PRIMARY KEY,
    user_id                       uuid         NOT NULL REFERENCES app_user (id),
    order_number                  varchar(20)  NOT NULL UNIQUE,
    status                        varchar(20)  NOT NULL,
    placed_at                     timestamptz  NOT NULL,
    idempotency_key               varchar(64),
    subtotal_cents                bigint       NOT NULL,
    discount_total_cents          bigint       NOT NULL,
    delivery_fee_cents            bigint       NOT NULL,
    total_cents                   bigint       NOT NULL,
    currency                      varchar(3)      NOT NULL DEFAULT 'EUR',
    slot_id                       uuid         NOT NULL REFERENCES delivery_slot (id),
    slot_starts_at                timestamptz  NOT NULL,
    slot_ends_at                  timestamptz  NOT NULL,
    slot_order_cutoff_at          timestamptz  NOT NULL,
    address_id                    uuid         NOT NULL,
    address_label                 varchar(50),
    address_street                varchar(120) NOT NULL,
    address_house_number          varchar(10)  NOT NULL,
    address_addition              varchar(50),
    address_postal_code           varchar(5)      NOT NULL,
    address_city                  varchar(80)  NOT NULL,
    address_country_code          varchar(2)      NOT NULL,
    address_delivery_instructions varchar(300),
    version                       bigint       NOT NULL DEFAULT 0,
    created_at                    timestamptz  NOT NULL,
    updated_at                    timestamptz  NOT NULL,
    CONSTRAINT ck_customer_order_status CHECK (status IN ('PLACED', 'CANCELLED', 'OUT_FOR_DELIVERY', 'DELIVERED')),
    CONSTRAINT ck_customer_order_totals CHECK (total_cents = subtotal_cents - discount_total_cents + delivery_fee_cents)
);
CREATE INDEX ix_customer_order_user_placed ON customer_order (user_id, placed_at DESC);
CREATE UNIQUE INDEX uq_customer_order_idempotency ON customer_order (user_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX ix_customer_order_slot ON customer_order (slot_id);

CREATE TABLE order_item (
    id               uuid PRIMARY KEY,
    order_id         uuid         NOT NULL REFERENCES customer_order (id) ON DELETE CASCADE,
    line_no          integer      NOT NULL,
    product_id       uuid         NOT NULL REFERENCES product (id),
    name             varchar(150) NOT NULL,
    unit_price_cents bigint       NOT NULL,
    quantity         integer      NOT NULL,
    line_total_cents bigint       NOT NULL,
    CONSTRAINT uq_order_item_line UNIQUE (order_id, line_no),
    CONSTRAINT ck_order_item_quantity CHECK (quantity >= 1)
);

-- Recipes: schema only, reserved for a future iteration (no API yet).
CREATE TABLE recipe (
    id               uuid PRIMARY KEY,
    slug             varchar(80)  NOT NULL UNIQUE,
    title            varchar(150) NOT NULL,
    description      text,
    servings         integer      NOT NULL,
    prep_minutes     integer,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    CONSTRAINT ck_recipe_servings CHECK (servings >= 1)
);

CREATE TABLE recipe_ingredient (
    id          uuid PRIMARY KEY,
    recipe_id   uuid         NOT NULL REFERENCES recipe (id) ON DELETE CASCADE,
    product_id  uuid REFERENCES product (id) ON DELETE SET NULL,
    description varchar(150) NOT NULL,
    quantity    varchar(40),
    sort_order  integer      NOT NULL
);
CREATE INDEX ix_recipe_ingredient_recipe ON recipe_ingredient (recipe_id);
