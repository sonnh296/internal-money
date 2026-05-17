-- POS Boost — MySQL (boost-mysql-1, database: rewards)
-- Chạy:
--   docker exec -i boost-mysql-1 mysql -urewards -prewards rewards < scripts/sql/reset-pos-rewards-mysql.sql

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE reward_ledger;
TRUNCATE TABLE customer_balance;
SET FOREIGN_KEY_CHECKS = 1;
