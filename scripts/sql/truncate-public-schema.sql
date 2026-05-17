-- Truncate tất cả bảng trong schema public (dùng cho DB không cần giữ dòng nào).
-- Chạy: docker exec -i docker-common-postgres psql -U postgres -d <dbname> -f scripts/sql/truncate-public-schema.sql

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN (
    SELECT quote_ident(schemaname) AS schemaname, quote_ident(tablename) AS tablename
    FROM pg_tables
    WHERE schemaname = 'public'
    ORDER BY tablename
  ) LOOP
    EXECUTE format('TRUNCATE TABLE %s.%s RESTART IDENTITY CASCADE', r.schemaname, r.tablename);
  END LOOP;
END $$;
