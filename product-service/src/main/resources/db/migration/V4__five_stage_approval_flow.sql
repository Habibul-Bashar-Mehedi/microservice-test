ALTER TABLE products RENAME COLUMN maintainer_reviewer TO manager_reviewer;
ALTER TABLE products ADD COLUMN specialist_reviewer VARCHAR(255);
ALTER TABLE products ADD COLUMN salesman_reviewer VARCHAR(255);

UPDATE products SET status = 'PENDING_MANAGER' WHERE status = 'PENDING_MAINTAINER';
UPDATE products SET status = 'REJECTED_BY_MANAGER' WHERE status = 'REJECTED_BY_MAINTAINER';
