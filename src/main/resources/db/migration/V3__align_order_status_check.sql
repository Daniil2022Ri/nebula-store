-- Databases created before DELIVERED was introduced were baselined at V1,
-- so V1 never re-ran for them and their status check still rejects the new value.
-- Recreating the constraint keeps every installation in the same shape.

ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;

ALTER TABLE orders ADD CONSTRAINT orders_status_check
    CHECK (status IN ('NEW', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED'));
