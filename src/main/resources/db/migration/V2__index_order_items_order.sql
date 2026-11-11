-- Order lines are always loaded by order id; without this index every order page
-- scan falls back to a sequential scan once the table grows.

CREATE INDEX idx_order_items_order ON order_items (order_id);
