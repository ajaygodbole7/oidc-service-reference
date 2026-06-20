CREATE TABLE payment_authorizations (
    payment_id          VARCHAR(255) PRIMARY KEY,
    order_id            VARCHAR(255) NOT NULL,
    user_sub            VARCHAR(255) NOT NULL,
    amount_amount       NUMERIC(12, 2) NOT NULL,
    amount_currency     VARCHAR(3) NOT NULL,
    status              VARCHAR(32) NOT NULL,
    idempotency_key     VARCHAR(255) NOT NULL UNIQUE,
    command_fingerprint VARCHAR(255) NOT NULL,
    authorized_at       TIMESTAMPTZ NOT NULL
);
