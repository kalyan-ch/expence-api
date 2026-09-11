-- ----------------------------------------------------------------------------
-- SEED DEFAULT CATEGORIES
-- Invariant: a fresh database is usable immediately -- 3 INCOME and 9 EXPENSE
-- categories exist with is_default = true. Defaults are renameable but never
-- deletable, which is what is_default marks.
-- ----------------------------------------------------------------------------

INSERT INTO categories (name, kind, is_default) VALUES
  ('Salary',        'INCOME',  true),
  ('Interest',      'INCOME',  true),
  ('Other Income',  'INCOME',  true),
  ('Groceries',     'EXPENSE', true),
  ('Rent',          'EXPENSE', true),
  ('Utilities',     'EXPENSE', true),
  ('Transport',     'EXPENSE', true),
  ('Dining',        'EXPENSE', true),
  ('Health',        'EXPENSE', true),
  ('Shopping',      'EXPENSE', true),
  ('Entertainment', 'EXPENSE', true),
  ('Other',         'EXPENSE', true);
