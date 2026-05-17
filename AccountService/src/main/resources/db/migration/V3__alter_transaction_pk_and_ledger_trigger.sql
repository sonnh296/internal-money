-- 1. Đổi PK account_transaction từ BIGSERIAL (id) sang UUID (transaction_id)
ALTER TABLE account_transaction DROP CONSTRAINT account_transaction_pkey CASCADE;
ALTER TABLE account_transaction DROP COLUMN id;
ALTER TABLE account_transaction ADD PRIMARY KEY (transaction_id);

-- 2. Thêm ràng buộc DB “tổng DEBIT = CREDIT” theo transaction_group_id cho sổ cái (transaction_ledger)
-- Sử dụng trigger kiểm tra tổng sau mỗi giao dịch

CREATE OR REPLACE FUNCTION check_ledger_balance()
RETURNS TRIGGER AS $$
DECLARE
    balance DECIMAL(19,4);
BEGIN
    SELECT COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END), 0) -
           COALESCE(SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END), 0)
    INTO balance
    FROM transaction_ledger
    WHERE transaction_group_id = NEW.transaction_group_id;

    IF balance != 0 THEN
        RAISE EXCEPTION 'Ledger out of balance for group %: difference is %', NEW.transaction_group_id, balance;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Constraint deferred để cho phép ghi DEBIT và CREDIT lần lượt trong cùng transaction rồi mới kiểm tra lúc COMMIT
CREATE CONSTRAINT TRIGGER trg_check_ledger_balance
    AFTER INSERT OR UPDATE ON transaction_ledger
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
    EXECUTE FUNCTION check_ledger_balance();
