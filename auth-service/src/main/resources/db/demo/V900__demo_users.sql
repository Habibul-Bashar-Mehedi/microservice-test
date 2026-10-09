-- Demo/test data for a fresh installation.
-- Applied ONLY when the "demo" Spring profile is active (see application-demo.yaml,
-- which adds classpath:db/demo to the Flyway locations). Never runs in production.
--
-- One account per role. Emails are synthetic and safe for demos/tests.
-- Authentication is Google Sign-In only; these records ensure that when a user logs
-- in with a Google account whose email matches one below, the correct role is applied
-- and the account is already active in user-service.

INSERT INTO auth_users (id, name, email, role)
VALUES
    (1, 'Demo User',          'user@example.com',          'USER'),
    (2, 'Demo Maintainer',    'maintainer@example.com',    'MAINTAINER'),
    (3, 'Demo Manager',       'manager@example.com',       'MANAGER'),
    (4, 'Demo Specialist',    'specialist@example.com',    'PRODUCT_SPECIALIST'),
    (5, 'Demo Salesman',      'salesman@example.com',      'SALESMAN'),
    (6, 'Demo Admin',         'admin@example.com',         'ADMIN')
ON CONFLICT (email) DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('auth_users', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 0) FROM auth_users) + 1, 1)
);