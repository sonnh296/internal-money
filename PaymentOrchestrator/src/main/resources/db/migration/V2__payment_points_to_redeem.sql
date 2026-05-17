-- Lưu điểm redeem sau POSTED; bổ sung cột thiếu trên DB legacy (baseline trước Flyway)
ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS points_to_redeem BIGINT NOT NULL DEFAULT 0;
