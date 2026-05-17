-- Chỉ authdb: xóa refresh token + mọi user trừ SUPER_ADMIN admin.demo@mockbank.local
-- Chạy:
--   docker exec -i docker-common-postgres psql -U postgres -d authdb -f scripts/sql/reset-authdb-except-admin.sql

BEGIN;

DELETE FROM refresh_tokens;

DELETE FROM auth_users
WHERE NOT (
    role = 'SUPER_ADMIN'
    AND lower(trim(email)) = lower(trim('admin.demo@mockbank.local'))
)
;

COMMIT;

-- Kiểm tra:
-- SELECT email, role, customer_id, enabled FROM auth_users;
