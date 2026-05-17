-- =============================================================================
-- MockBank — Xóa toàn bộ dữ liệu local, GIỮ tài khoản SUPER_ADMIN demo
-- =============================================================================
-- Chạy khi Postgres/MySQL đang chạy (docker-common-postgres, boost-mysql-1).
--
-- Tài khoản admin được giữ (mặc định dev):
--   Email:    admin.demo@mockbank.local
--   Password: Admin@12345  (DemoAdminSeeder reset lại mỗi lần khởi động AuthUser)
--   Role:     SUPER_ADMIN
--   Customer: admin-root-0001
--
-- Cách chạy (từng DB Postgres):
--   docker exec -i docker-common-postgres psql -U postgres -d authdb       < scripts/sql/reset-except-admin.sql
--   docker exec -i docker-common-postgres psql -U postgres -d customerdb  -c "$(sed -n '/^-- @DB:customerdb/,/^-- @END/p' scripts/sql/reset-except-admin.sql | grep -v '^--')"
--
-- Hoặc dùng shell: ./scripts/reset-local-data.sh
-- =============================================================================

-- -----------------------------------------------------------------------------
-- @DB:authdb — Giữ SUPER_ADMIN admin.demo@mockbank.local
-- -----------------------------------------------------------------------------
-- \c authdb   -- (bỏ comment nếu chạy trong psql interactive)

BEGIN;

DELETE FROM refresh_tokens;

DELETE FROM auth_users
WHERE NOT (
    role = 'SUPER_ADMIN'
    AND lower(trim(email)) = lower(trim('admin.demo@mockbank.local'))
);

COMMIT;

-- -----------------------------------------------------------------------------
-- @DB:customerdb — Xóa hết khách hàng (admin không có bản ghi Customer)
-- -----------------------------------------------------------------------------

-- Chạy: psql -U postgres -d customerdb -f phần dưới
-- BEGIN;
-- TRUNCATE TABLE customers RESTART IDENTITY CASCADE;
-- COMMIT;

-- -----------------------------------------------------------------------------
-- @DB:accountsdb
-- -----------------------------------------------------------------------------

-- BEGIN;
-- TRUNCATE TABLE account_transaction RESTART IDENTITY CASCADE;
-- TRUNCATE TABLE account_hold RESTART IDENTITY CASCADE;
-- TRUNCATE TABLE account RESTART IDENTITY CASCADE;
-- COMMIT;

-- -----------------------------------------------------------------------------
-- @DB:billerdb
-- -----------------------------------------------------------------------------

-- BEGIN;
-- TRUNCATE TABLE invoices RESTART IDENTITY CASCADE;
-- TRUNCATE TABLE subscriptions RESTART IDENTITY CASCADE;
-- TRUNCATE TABLE billers RESTART IDENTITY CASCADE;
-- TRUNCATE TABLE service_packages RESTART IDENTITY CASCADE;
-- COMMIT;

-- -----------------------------------------------------------------------------
-- @DB:paymentdb
-- -----------------------------------------------------------------------------

-- BEGIN;
-- TRUNCATE TABLE retries RESTART IDENTITY CASCADE;
-- TRUNCATE TABLE processed_events RESTART IDENTITY CASCADE;
-- TRUNCATE TABLE outbox RESTART IDENTITY CASCADE;
-- TRUNCATE TABLE payments RESTART IDENTITY CASCADE;
-- COMMIT;

-- -----------------------------------------------------------------------------
-- @DB:billpayworkerdb
-- -----------------------------------------------------------------------------

-- BEGIN;
-- TRUNCATE TABLE batch_lines RESTART IDENTITY CASCADE;
-- TRUNCATE TABLE batches RESTART IDENTITY CASCADE;
-- COMMIT;

-- -----------------------------------------------------------------------------
-- @DB:settlementdb
-- -----------------------------------------------------------------------------

-- BEGIN;
-- TRUNCATE TABLE bill_batch_settlement RESTART IDENTITY CASCADE;
-- COMMIT;

-- -----------------------------------------------------------------------------
-- @DB:eftdb / @DB:eftworkerdb — thường trống; truncate mọi bảng public nếu có
-- -----------------------------------------------------------------------------

-- Dùng helper truncate_all_public_tables() bên dưới khi chạy từng DB.
