-- ----------------------------------------------------------------------------
-- TRANSACTIONS
-- Invariants enforced here, so no application bug can write a ledger row that
-- breaks the "balance is a single SUM" rule:
--   amount <> 0        a zero-money row is never meaningful.
--   sign_matches_type  INCOME is positive, EXPENSE is negative; amount carries
--                      its own sign so no sum ever branches on type. TRANSFER
--                      is exempt: its two legs are -x and +x.
--   transfer_shape     a TRANSFER row is uncategorised and always belongs to a
--                      transfer group; a non-TRANSFER row never has a group.
-- The three indexes serve the list filters (account + date), the report date
-- ranges, and fetching both legs of a transfer by its group.
-- ----------------------------------------------------------------------------

CREATE TABLE transactions (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id        UUID NOT NULL REFERENCES accounts(id),
  category_id       UUID     NULL REFERENCES categories(id),
  amount            NUMERIC(19,4) NOT NULL CHECK (amount <> 0),
  type              TEXT NOT NULL CHECK (type IN ('INCOME','EXPENSE','TRANSFER')),
  occurred_on       DATE NOT NULL,
  description       TEXT,
  transfer_group_id UUID NULL,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT transfer_shape CHECK (
    (type = 'TRANSFER' AND category_id IS NULL     AND transfer_group_id IS NOT NULL) OR
    (type <> 'TRANSFER' AND transfer_group_id IS NULL)
  ),
  CONSTRAINT sign_matches_type CHECK (
    (type = 'INCOME'  AND amount > 0) OR
    (type = 'EXPENSE' AND amount < 0) OR
    (type = 'TRANSFER')
  )
);

CREATE INDEX idx_tx_account_date ON transactions (account_id, occurred_on DESC);
CREATE INDEX idx_tx_date         ON transactions (occurred_on);
CREATE INDEX idx_tx_group        ON transactions (transfer_group_id);
