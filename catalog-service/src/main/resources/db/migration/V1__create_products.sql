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
-- (including SEC-CATALOG-ANONYMOUS-READ-ONLY's /products/6801HWW000000) work on a fresh database.
-- Ids are canonical 13-char TSIDs (time-sortable), so ORDER BY id is the keyset cursor order.
INSERT INTO products (id, sku, name, price_amount, price_currency, inventory_status, store_id) VALUES
    ('6801HWW000000', 'MUG-001', 'Starter Mug', 12.50, 'USD', 'IN_STOCK', 'main'),
    ('6801HWW00YGJ3', 'BAG-002', 'Travel Bag', 48.00, 'USD', 'LOW_STOCK', 'main'),
    ('6801HWW01X146', 'LAMP-003', 'Desk Lamp', 34.00, 'USD', 'IN_STOCK', 'main'),
    ('6801HWW02VHP9', 'NOTE-004', 'Field Notebook', 9.00, 'USD', 'IN_STOCK', 'main'),
    ('6801HWW03T28C', 'TOTE-005', 'Canvas Tote', 22.00, 'USD', 'LOW_STOCK', 'main');
