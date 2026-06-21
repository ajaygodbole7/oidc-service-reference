CREATE TABLE orders (
    id                       VARCHAR(255) PRIMARY KEY,
    owner_sub                VARCHAR(255) NOT NULL,
    source_cart_id           VARCHAR(255) NOT NULL,
    total_amount             NUMERIC(12, 2) NOT NULL,
    total_currency           VARCHAR(3) NOT NULL,
    payment_authorization_id VARCHAR(255) NOT NULL,
    created_at               TIMESTAMPTZ NOT NULL,
    status                   VARCHAR(32) NOT NULL
);

CREATE TABLE order_lines (
    order_id            VARCHAR(255) NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    line_position       INTEGER NOT NULL,
    product_id          VARCHAR(255) NOT NULL,
    quantity            INTEGER NOT NULL,
    unit_price_amount   NUMERIC(12, 2) NOT NULL,
    unit_price_currency VARCHAR(3) NOT NULL,
    PRIMARY KEY (order_id, line_position)
);

CREATE TABLE order_idempotency (
    id                  BIGSERIAL PRIMARY KEY,
    subject             VARCHAR(255) NOT NULL,
    idempotency_key     VARCHAR(120) NOT NULL,
    request_fingerprint VARCHAR(255) NOT NULL,
    order_id            VARCHAR(255) NOT NULL,
    UNIQUE (subject, idempotency_key)
);

-- Seed parity with InMemoryOrderRepository.withLocalFixtures.
INSERT INTO orders (
    id,
    owner_sub,
    source_cart_id,
    total_amount,
    total_currency,
    payment_authorization_id,
    created_at,
    status
) VALUES (
    'alice-order',
    'alice',
    'alice-cart',
    12.50,
    'USD',
    'local-auth-alice-order',
    '2026-06-20T00:00:00Z',
    'CONFIRMED'
);

INSERT INTO order_lines (
    order_id,
    line_position,
    product_id,
    quantity,
    unit_price_amount,
    unit_price_currency
) VALUES (
    'alice-order',
    0,
    'starter-mug',
    1,
    12.50,
    'USD'
);
