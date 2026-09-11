-- ----------------------------------------------------------------------------
-- ACCOUNTS
-- Invariant: an account's name is unique, and its type is one of the four
-- Phase 1 kinds. opening_balance is the only balance ever stored; the live
-- balance is always derived as opening_balance + COALESCE(SUM(amount), 0).
-- ----------------------------------------------------------------------------

CREATE TABLE accounts (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name            TEXT NOT NULL UNIQUE,
  type            TEXT NOT NULL CHECK (type IN ('CHECKING','SAVINGS','CREDIT_CARD','CASH')),
  opening_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
