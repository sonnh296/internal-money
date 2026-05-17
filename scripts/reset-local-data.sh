#!/usr/bin/env bash
# Xóa dữ liệu local: reset các DB dịch vụ + giữ admin SUPER_ADMIN trong authdb.
# Sau khi chạy, khởi động lại AuthUser (dev) để DemoAdminSeeder đồng bộ mật khẩu admin.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-docker-common-postgres}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-boost-mysql-1}"
PGUSER="${PGUSER:-postgres}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin.demo@mockbank.local}"

SERVICE_DBS=(
  customerdb
  accountsdb
  billerdb
  paymentdb
  billpayworkerdb
  settlementdb
  eftdb
  eftworkerdb
)

echo "[reset] Postgres container: ${POSTGRES_CONTAINER}"
docker ps --format '{{.Names}}' | grep -qx "${POSTGRES_CONTAINER}"

echo "[reset] Drop + recreate service databases..."
for db in "${SERVICE_DBS[@]}"; do
  docker exec "${POSTGRES_CONTAINER}" psql -U "${PGUSER}" -d postgres -v ON_ERROR_STOP=1 \
    -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '${db}' AND pid <> pg_backend_pid();" \
    2>/dev/null || true
  docker exec "${POSTGRES_CONTAINER}" psql -U "${PGUSER}" -d postgres -v ON_ERROR_STOP=1 \
    -c "DROP DATABASE IF EXISTS ${db};"
  docker exec "${POSTGRES_CONTAINER}" psql -U "${PGUSER}" -d postgres -v ON_ERROR_STOP=1 \
    -c "CREATE DATABASE ${db};"
done

echo "[reset] authdb — giữ admin: ${ADMIN_EMAIL}"
docker exec -i "${POSTGRES_CONTAINER}" psql -U "${PGUSER}" -d authdb -v ON_ERROR_STOP=1 <<SQL
BEGIN;
DELETE FROM refresh_tokens;
DELETE FROM auth_users
WHERE NOT (
    role = 'SUPER_ADMIN'
    AND lower(trim(email)) = lower(trim('${ADMIN_EMAIL}'))
);
COMMIT;
SELECT email, role, customer_id FROM auth_users;
SQL

if docker ps --format '{{.Names}}' | grep -qx "${MYSQL_CONTAINER}"; then
  echo "[reset] POS rewards (MySQL)..."
  docker exec -i "${MYSQL_CONTAINER}" mysql -urewards -prewards rewards -v ON_ERROR_STOP=1 <<'SQL'
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE reward_ledger;
TRUNCATE TABLE customer_balance;
SET FOREIGN_KEY_CHECKS = 1;
SQL
else
  echo "[reset] Skip MySQL (${MYSQL_CONTAINER} not running)."
fi

echo ""
echo "[reset] Done."
echo "  Admin login: ${ADMIN_EMAIL} / Admin@12345"
echo "  Khởi động lại AuthUser (profile dev) để seeder cập nhật hash mật khẩu nếu cần."
echo "  Khởi động lại các service khác để Hibernate tạo lại bảng (ddl-auto update)."
