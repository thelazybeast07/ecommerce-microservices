CREATE TABLE orders (
    id                    UUID           PRIMARY KEY,

    -- Belongs to user-service. Deliberately NO foreign key: cross-service references are
    -- validated through the owning service's API, never by the database.
    customer_id           UUID           NOT NULL,

    status                VARCHAR(20)    NOT NULL,
    total_amount          NUMERIC(19,2)  NOT NULL,
    currency              VARCHAR(3)     NOT NULL,

    -- Shipping address SNAPSHOT, copied from user-service when the order was placed.
    -- Intentional denormalisation: the customer may later edit or delete that address, and
    -- the order must still show where it was actually shipped.
    shipping_address_id   UUID,
    shipping_line1        VARCHAR(255)   NOT NULL,
    shipping_line2        VARCHAR(255),
    shipping_city         VARCHAR(100)   NOT NULL,
    shipping_state        VARCHAR(100),
    shipping_postal_code  VARCHAR(20)    NOT NULL,
    shipping_country      VARCHAR(2)     NOT NULL,

    version               BIGINT         NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ    NOT NULL,
    updated_at            TIMESTAMPTZ    NOT NULL,

    CONSTRAINT ck_orders_total    CHECK (total_amount >= 0),
    CONSTRAINT ck_orders_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_orders_status   CHECK (status IN (
        'PENDING', 'CONFIRMED', 'PAYMENT_PENDING', 'PAID',
        'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED'))
);

-- Supports GET /customers/{customerId}/orders, newest first.
CREATE INDEX idx_orders_customer_id_created_at ON orders (customer_id, created_at DESC);
