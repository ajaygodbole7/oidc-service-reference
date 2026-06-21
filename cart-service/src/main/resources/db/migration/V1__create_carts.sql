CREATE TABLE carts (
    id        VARCHAR(255) PRIMARY KEY,
    owner_sub VARCHAR(255) NOT NULL UNIQUE,
    -- Optimistic-lock counter. NOT NULL DEFAULT 0 so the seed insert below (which omits
    -- the column) and any raw insert land at version 0; Spring Data JDBC drives it from
    -- CartRow.@Version thereafter (WHERE version = ? on update, bumped on success).
    version   BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE cart_items (
    cart_id             VARCHAR(255) NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    item_position       INTEGER NOT NULL,
    product_id          VARCHAR(255) NOT NULL,
    quantity            INTEGER NOT NULL,
    unit_price_amount   NUMERIC(12, 2) NOT NULL,
    unit_price_currency VARCHAR(3) NOT NULL,
    PRIMARY KEY (cart_id, item_position)
);

-- Seed parity with InMemoryCartRepository.withLocalFixtures: empty carts for alice and bob.
INSERT INTO carts (id, owner_sub) VALUES ('alice-cart', 'alice'), ('bob-cart', 'bob');
