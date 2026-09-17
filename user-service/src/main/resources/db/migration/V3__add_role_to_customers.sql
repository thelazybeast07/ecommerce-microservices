-- Authentication needs to know WHAT a caller may do, not just who they are.
--
-- The DEFAULT matters: customers already exist in this table, and a NOT NULL column
-- cannot be added to a populated table without telling PostgreSQL what to put in the
-- existing rows. Every current customer becomes a CUSTOMER, which is correct.
ALTER TABLE customers
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER';

ALTER TABLE customers
    ADD CONSTRAINT ck_customers_role CHECK (role IN ('CUSTOMER', 'ADMIN'));
