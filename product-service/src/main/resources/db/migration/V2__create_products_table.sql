CREATE TABLE products (
    id          UUID           PRIMARY KEY,
    sku         VARCHAR(64)    NOT NULL,
    name        VARCHAR(200)   NOT NULL,
    description VARCHAR(2000),
    price       NUMERIC(19,2)  NOT NULL,
    currency    VARCHAR(3)     NOT NULL,
    category_id UUID           NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    version     BIGINT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ    NOT NULL,
    updated_at  TIMESTAMPTZ    NOT NULL,

    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT uk_products_sku      UNIQUE (sku),
    CONSTRAINT ck_products_price    CHECK (price >= 0),
    CONSTRAINT ck_products_currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_products_status   CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- PostgreSQL does not index foreign-key columns automatically.
CREATE INDEX idx_products_category_id ON products (category_id);

-- Supports GET /products?status=ACTIVE&sort=createdAt,desc (the default catalogue listing).
CREATE INDEX idx_products_status_created_at ON products (status, created_at DESC);
