-- Demo/test data for a fresh installation.
-- Applied ONLY when the "demo" Spring profile is active (application-demo.yaml adds
-- classpath:db/demo to the Flyway locations). Never runs in production.
--
-- Sample orders reference the seeded demo data:
--   user_id     -> ids 1..6 seeded in the user-service demo migration
--   product_id  -> ids 1..10 seeded in the product-service demo migration
-- These ids are stable only on a fresh installation where the demo migrations run on
-- empty databases (which is the intended clean-install scenario).
-- States cover CONFIRMED, PENDING and REJECTED so every status badge is demonstrable.

INSERT INTO orders (id, user_id, product_id, quantity, status, product_updated)
VALUES
    (1, 1, 2,  4, 'CONFIRMED', true),
    (2, 1, 1,  2, 'PENDING',   false),
    (3, 6, 4,  1, 'CONFIRMED', true),
    (4, 1, 10, 1, 'REJECTED',  false)
ON CONFLICT DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('orders', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 0) FROM orders) + 1, 1)
);

-- A couple of cart items for the demo user so the cart page is not empty.
INSERT INTO cart_items (id, user_id, product_id, name, price, quantity, available_quantity)
VALUES
    (1, 1, 2, 'Chal 25kg',       2000.00, 3, 120),
    (2, 1, 8, 'Wireless Mouse',  1500.00, 1, 45)
ON CONFLICT (user_id, product_id) DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('cart_items', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 0) FROM cart_items) + 1, 1)
);