-- Demo/test data for a fresh installation.
-- Applied ONLY when the "demo" Spring profile is active (application-demo.yaml adds
-- classpath:db/demo to the Flyway locations). Never runs in production.
--
-- Mirrors the auth-service demo accounts. active = true so Google Sign-In with a
-- matching email is accepted and the seeded role is applied.
-- ids 1..6 are intentional: the order-service demo data references these user ids.

INSERT INTO users (id, name, email, active, role)
VALUES
    (1, 'Demo User',          'user@example.com',          true, 'USER'),
    (2, 'Demo Maintainer',    'maintainer@example.com',    true, 'MAINTAINER'),
    (3, 'Demo Manager',       'manager@example.com',       true, 'MANAGER'),
    (4, 'Demo Specialist',    'specialist@example.com',    true, 'PRODUCT_SPECIALIST'),
    (5, 'Demo Salesman',      'salesman@example.com',      true, 'SALESMAN'),
    (6, 'Demo Admin',         'admin@example.com',         true, 'ADMIN')
ON CONFLICT (email) DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('users', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 0) FROM users) + 1, 1)
);