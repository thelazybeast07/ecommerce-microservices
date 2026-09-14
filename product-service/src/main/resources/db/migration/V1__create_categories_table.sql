CREATE TABLE categories (
    id          UUID         PRIMARY KEY,
    name        VARCHAR(150) NOT NULL,
    description VARCHAR(1000),
    status      VARCHAR(20)  NOT NULL,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uk_categories_name  UNIQUE (name),
    CONSTRAINT ck_categories_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
