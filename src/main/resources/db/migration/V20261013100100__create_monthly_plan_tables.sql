-- ----------------------------------------------------------------------------
-- MONTHLY PLAN
-- Replicates the Excel bill sheet: a template of recurring bills and paychecks,
-- copied into an editable plan once per month.
-- Invariants:
--   amounts are positive magnitudes; kind decides the direction. NULL means
--   "nothing due this month" (a blank cell in the sheet).
--   a month is stored as its first day, and exists at most once.
--   a starting balance is typed in once per account per month.
--   a recurring item is copied into a given month at most once.
--   only a PAID item carries a paid date.
-- ----------------------------------------------------------------------------

CREATE TABLE recurring_items (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name           TEXT NOT NULL UNIQUE,
  kind           TEXT NOT NULL CHECK (kind IN ('INCOME','EXPENSE','CARD_PAYMENT','SAVINGS')),
  account_id     UUID NOT NULL REFERENCES accounts(id),
  default_amount NUMERIC(19,4) NULL CHECK (default_amount > 0),
  billing_day    INT      NULL CHECK (billing_day BETWEEN 1 AND 31),
  sort_order     INT NOT NULL DEFAULT 0,
  active         BOOLEAN NOT NULL DEFAULT true,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE months (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  month      DATE NOT NULL UNIQUE CHECK (EXTRACT(DAY FROM month) = 1),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE month_balances (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  month_id         UUID NOT NULL REFERENCES months(id) ON DELETE CASCADE,
  account_id       UUID NOT NULL REFERENCES accounts(id),
  starting_balance NUMERIC(19,4) NOT NULL,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (month_id, account_id)
);

CREATE TABLE month_items (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  month_id          UUID NOT NULL REFERENCES months(id) ON DELETE CASCADE,
  recurring_item_id UUID NULL REFERENCES recurring_items(id),
  name              TEXT NOT NULL,
  kind              TEXT NOT NULL CHECK (kind IN ('INCOME','EXPENSE','CARD_PAYMENT','SAVINGS')),
  account_id        UUID NOT NULL REFERENCES accounts(id),
  amount            NUMERIC(19,4) NULL CHECK (amount > 0),
  billing_day       INT      NULL CHECK (billing_day BETWEEN 1 AND 31),
  sort_order        INT NOT NULL DEFAULT 0,
  status            TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PAID')),
  paid_on           DATE NULL,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (month_id, recurring_item_id),
  CONSTRAINT paid_on_only_when_paid CHECK (status = 'PAID' OR paid_on IS NULL)
);

CREATE INDEX idx_month_items_month ON month_items (month_id);

CREATE TRIGGER trg_recurring_items_updated_at
    BEFORE UPDATE ON recurring_items FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_months_updated_at
    BEFORE UPDATE ON months FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_month_balances_updated_at
    BEFORE UPDATE ON month_balances FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_month_items_updated_at
    BEFORE UPDATE ON month_items FOR EACH ROW EXECUTE FUNCTION set_updated_at();
