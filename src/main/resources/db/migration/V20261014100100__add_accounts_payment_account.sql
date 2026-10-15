-- ----------------------------------------------------------------------------
-- PAYMENT ACCOUNTS
-- Invariant: bills and paychecks are assigned only to accounts flagged as
-- payment accounts. Accounts already used by the plan are flagged so existing
-- data stays valid.
-- ----------------------------------------------------------------------------

ALTER TABLE accounts ADD COLUMN payment_account BOOLEAN NOT NULL DEFAULT false;

UPDATE accounts SET payment_account = true
WHERE id IN (SELECT account_id FROM recurring_items
             UNION SELECT account_id FROM month_items
             UNION SELECT account_id FROM month_balances);
