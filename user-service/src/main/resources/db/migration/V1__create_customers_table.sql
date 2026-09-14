-- Customers: the aggregate root of the user-service.
CREATE TABLE customers (
    id            UUID         PRIMARY KEY,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(254) NOT NULL,
    phone         VARCHAR(20),
    password_hash VARCHAR(100) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uk_customers_email      UNIQUE (email),
    -- The application lower-cases emails; the database guarantees it, so the
    -- unique constraint can never be bypassed by "Jane@x.com" vs "jane@x.com".
    CONSTRAINT ck_customers_email_lower CHECK (email = LOWER(email)),
    CONSTRAINT ck_customers_status     CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- Supports the admin listing: filter by status, newest first.
CREATE INDEX idx_customers_status_created_at ON customers (status, created_at DESC);
