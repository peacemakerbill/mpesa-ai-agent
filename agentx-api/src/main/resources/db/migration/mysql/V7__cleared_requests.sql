-- A customer can clear a finished request from their list. The row stays, for reports and for disputes.
ALTER TABLE wallet_actions ADD COLUMN cleared_at DATETIME(6) NULL;
