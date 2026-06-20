CREATE TABLE products (
    id               VARCHAR(255) PRIMARY KEY,
    sku              VARCHAR(255) NOT NULL UNIQUE,
    name             VARCHAR(255) NOT NULL,
    price_amount     NUMERIC(12, 2) NOT NULL,
    price_currency   VARCHAR(3) NOT NULL,
    inventory_status VARCHAR(32) NOT NULL,
    store_id         VARCHAR(255) NOT NULL
);

-- Seed parity with InMemoryProductRepository.withLocalFixtures so anonymous catalog reads
-- (including SEC-CATALOG-ANONYMOUS-READ-ONLY's /products/starter-mug) work on a fresh database.
INSERT INTO products (id, sku, name, price_amount, price_currency, inventory_status, store_id) VALUES
    ('starter-mug', 'MUG-001', 'Starter Mug', 12.50, 'USD', 'IN_STOCK', 'main'),
    ('travel-bag', 'BAG-002', 'Travel Bag', 48.00, 'USD', 'LOW_STOCK', 'main');
