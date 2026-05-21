CREATE INDEX idx_acc_hold_active ON account_hold (account_id, status) WHERE status = 'ACTIVE';
