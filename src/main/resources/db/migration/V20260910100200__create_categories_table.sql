-- ----------------------------------------------------------------------------
-- CATEGORIES
-- Invariant: a category is typed INCOME or EXPENSE, and a name is unique only
-- within its kind -- "Other" may exist once as income and once as expense.
-- is_default marks the seeded set, which is renameable but not deletable.
-- ----------------------------------------------------------------------------

CREATE TABLE categories (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name       TEXT NOT NULL,
  kind       TEXT NOT NULL CHECK (kind IN ('INCOME','EXPENSE')),
  is_default BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (name, kind)
);
