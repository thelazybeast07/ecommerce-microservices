-- Addresses belong to a customer. Both tables live in the SAME service database,
-- so a real foreign key is correct here. (Cross-service references never get FKs.)
CREATE TABLE addresses (
    id            UUID         PRIMARY KEY,
    customer_id   UUID         NOT NULL,
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255),
    city          VARCHAR(100) NOT NULL,
    state         VARCHAR(100),
    postal_code   VARCHAR(20)  NOT NULL,
    country       VARCHAR(2)   NOT NULL,
    address_type  VARCHAR(20)  NOT NULL,

    CONSTRAINT fk_addresses_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT ck_addresses_country  CHECK (country ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_addresses_type     CHECK (address_type IN ('SHIPPING', 'BILLING'))
);

-- PostgreSQL does not index foreign-key columns automatically.
CREATE INDEX idx_addresses_customer_id ON addresses (customer_id);
