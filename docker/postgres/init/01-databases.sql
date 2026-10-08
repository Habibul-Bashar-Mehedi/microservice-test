-- Creates one logical database per service (database-per-service pattern).
-- Runs as the POSTGRES_USER (lemon), which owns the created databases.
CREATE DATABASE "auth-service";
CREATE DATABASE "user-service";
CREATE DATABASE "product-service";
CREATE DATABASE "order-service";
CREATE DATABASE "log-db";
