-- 1. Rollback Ràng buộc DB “tổng DEBIT = CREDIT”
DROP TRIGGER IF EXISTS trg_check_ledger_balance ON transaction_ledger;
DROP FUNCTION IF EXISTS check_ledger_balance();

-- 2. Rollback PK account_transaction
ALTER TABLE account_transaction DROP CONSTRAINT account_transaction_pkey CASCADE;
ALTER TABLE account_transaction ADD COLUMN id BIGSERIAL PRIMARY KEY;
