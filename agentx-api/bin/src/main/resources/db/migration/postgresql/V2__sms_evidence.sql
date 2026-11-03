-- How well a completion is backed up. NONE, REPORTED (customer said so), SMS_AMOUNT or SMS_MATCHED (an M-Pesa
-- message on the customer's own phone matched). None of these mean Safaricom confirmed anything to us.
ALTER TABLE wallet_actions ADD COLUMN verification VARCHAR(12) NOT NULL DEFAULT 'NONE';
ALTER TABLE wallet_actions ADD COLUMN sms_code VARCHAR(12);
ALTER TABLE wallet_actions ADD COLUMN sms_matched_at TIMESTAMP WITH TIME ZONE;

-- One M-Pesa message can back up one request only. Nulls are allowed many times.
CREATE UNIQUE INDEX uk_wallet_actions_sms_code ON wallet_actions (sms_code);
