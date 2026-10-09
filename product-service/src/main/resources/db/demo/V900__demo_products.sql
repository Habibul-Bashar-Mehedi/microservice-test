-- Demo/test data for a fresh installation.
-- Applied ONLY when the "demo" Spring profile is active (application-demo.yaml adds
-- classpath:db/demo to the Flyway locations). Never runs in production.
--
-- Representative approved products across every category, including low-stock (3) and
-- out-of-stock (10) rows so the stock sorter and stock-state badges are demonstrable.
-- ids 1..10 are intentional: the order-service demo data references these product ids.
-- Approve status is seeded directly because fresh demo data must be visible without
-- walking the multi-stage approval workflow.

INSERT INTO products (id, name, price, available_quantity, category, status, created_by,
                      rejection_reason, rejected_by_role, manager_reviewer,
                      specialist_reviewer, salesman_reviewer, admin_reviewer)
VALUES
    (1,  'Ata 25kg Whole Wheat Flour', 5000.00, 50,  'ATA',    'APPROVED', 'maintainer@example.com', NULL, NULL, NULL, NULL, NULL, NULL),
    (2,  'Chal 25kg',                  2000.00, 120, 'CHAL',   'APPROVED', 'maintainer@example.com', NULL, NULL, NULL, NULL, NULL, NULL),
    (3,  'Chal 2kg',                   200.00,  8,   'CHAL',   'APPROVED', 'maintainer@example.com', NULL, NULL, NULL, NULL, NULL, NULL),
    (4,  'Chini 50kg',                 7000.00, 60,  'CHINI',  'APPROVED', 'maintainer@example.com', NULL, NULL, NULL, NULL, NULL, NULL),
    (5,  'Dal 50kg',                   3000.00, 80,  'DAL',    'APPROVED', 'maintainer@example.com', NULL, NULL, NULL, NULL, NULL, NULL),
    (6,  'Moshla 10kg',                1000.00, 40,  'MOSHLA', 'APPROVED', 'maintainer@example.com', NULL, NULL, NULL, NULL, NULL, NULL),
    (7,  'Moyda 50kg',                 4000.00, 70,  'MOYDA',  'APPROVED', 'maintainer@example.com', NULL, NULL, NULL, NULL, NULL, NULL),
    (8,  'Wireless Mouse',             1500.00, 45,  'OTHER',  'APPROVED', 'maintainer@example.com', NULL, NULL, NULL, NULL, NULL, NULL),
    (9,  'Bluetooth Speaker',          2800.00, 35,  'OTHER',  'APPROVED', 'maintainer@example.com', NULL, NULL, NULL, NULL, NULL, NULL),
    (10, 'Soybean Oil 5L',             1200.00, 0,   'OTHER',  'APPROVED', 'maintainer@example.com', NULL, NULL, NULL, NULL, NULL, NULL)
ON CONFLICT (name) DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('products', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 0) FROM products) + 1, 1)
);

-- A couple of demo notifications so the inbox has content.
INSERT INTO product_notifications (product_id, product_name, recipient_email, recipient_role,
                                   message, created_at, read_flag)
VALUES
    (1, 'Ata 25kg Whole Wheat Flour', 'maintainer@example.com', 'MAINTAINER',
     'Your product Ata 25kg Whole Wheat Flour was approved successfully.', now(), false),
    (4, 'Chini 50kg',                 'maintainer@example.com', 'MAINTAINER',
     'Your product Chini 50kg was approved successfully.', now(), false)
ON CONFLICT DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('product_notifications', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 0) FROM product_notifications) + 1, 1)
);