CREATE TABLE order_items (
    id         UUID           PRIMARY KEY,

    -- Same service, same database: a real foreign key is correct here.
    order_id   UUID           NOT NULL,

    -- Belongs to product-service: no foreign key, and the descriptive fields below are a
    -- snapshot so the order still reads correctly after the product changes or is retired.
    product_id UUID           NOT NULL,
    product_sku  VARCHAR(64)  NOT NULL,
    product_name VARCHAR(200) NOT NULL,

    quantity   INTEGER        NOT NULL,
    unit_price NUMERIC(19,2)  NOT NULL,
    subtotal   NUMERIC(19,2)  NOT NULL,

    CONSTRAINT fk_order_items_order  FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT ck_order_items_qty    CHECK (quantity > 0),
    CONSTRAINT ck_order_items_price  CHECK (unit_price >= 0),
    CONSTRAINT ck_order_items_total  CHECK (subtotal >= 0),
    -- One line per product per order; quantity carries the count.
    CONSTRAINT uk_order_items_order_product UNIQUE (order_id, product_id)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
