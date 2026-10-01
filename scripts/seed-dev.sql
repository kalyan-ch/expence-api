-- ----------------------------------------------------------------------------
-- DEV-ONLY demo data so the dashboard and reports have something to show.
-- NOT a Flyway migration: run by hand after the API has migrated the schema.
-- Re-runnable: accounts upsert by name, and every 'seed:' transaction is wiped
-- and regenerated for the six months ending today. setseed keeps it stable.
-- ----------------------------------------------------------------------------

BEGIN;

SELECT setseed(0.42);

INSERT INTO accounts (name, type, opening_balance) VALUES
  ('Main Checking', 'CHECKING',    2500),
  ('Rainy Day',     'SAVINGS',     8000),
  ('Visa',          'CREDIT_CARD',    0),
  ('Wallet',        'CASH',         150)
ON CONFLICT (name) DO NOTHING;

DELETE FROM transactions WHERE description LIKE 'seed:%';

-- Income and expenses.
WITH days AS (
  SELECT d::date AS d
  FROM generate_series(date_trunc('month', current_date) - interval '5 months', current_date, interval '1 day') d
),
months AS (SELECT d AS m FROM days WHERE extract(day FROM d) = 1),
seed (acct, cat, amount, type, occurred_on, description) AS (
            SELECT 'Main Checking', 'Salary',        5200::numeric,                        'INCOME',  m,      'seed: salary'      FROM months
  UNION ALL SELECT 'Main Checking', 'Rent',          -1800,                                'EXPENSE', m,      'seed: rent'        FROM months
  UNION ALL SELECT 'Main Checking', 'Utilities',     -round((110 + random() * 70)::numeric, 2), 'EXPENSE', m + 9,  'seed: utilities'   FROM months
  UNION ALL SELECT 'Rainy Day',     'Interest',      round((25 + random() * 10)::numeric, 2),   'INCOME',  m + 27, 'seed: interest'    FROM months
  UNION ALL SELECT 'Main Checking', 'Other Income',  round((150 + random() * 350)::numeric, 2),  'INCOME',  m + 14, 'seed: side gig'    FROM months WHERE random() < 0.5
  UNION ALL SELECT 'Visa',          'Groceries',     -round((60 + random() * 90)::numeric, 2),  'EXPENSE', d,      'seed: groceries'   FROM days WHERE extract(isodow FROM d) = 6
  UNION ALL SELECT 'Visa',          'Dining',        -round((15 + random() * 55)::numeric, 2),  'EXPENSE', d,      'seed: dining'      FROM days WHERE random() < 0.25
  UNION ALL SELECT 'Wallet',        'Transport',     -round((3 + random() * 25)::numeric, 2),   'EXPENSE', d,      'seed: transport'   FROM days WHERE random() < 0.30
  UNION ALL SELECT 'Visa',          'Shopping',      -round((20 + random() * 180)::numeric, 2), 'EXPENSE', d,      'seed: shopping'    FROM days WHERE random() < 0.08
  UNION ALL SELECT 'Visa',          'Entertainment', -round((10 + random() * 60)::numeric, 2),  'EXPENSE', d,      'seed: fun'         FROM days WHERE random() < 0.07
  UNION ALL SELECT 'Main Checking', 'Health',        -round((20 + random() * 150)::numeric, 2), 'EXPENSE', d,      'seed: pharmacy'    FROM days WHERE random() < 0.03
)
INSERT INTO transactions (account_id, category_id, amount, type, occurred_on, description)
SELECT a.id, c.id, s.amount, s.type, s.occurred_on, s.description
FROM seed s
JOIN accounts   a ON a.name = s.acct
JOIN categories c ON c.name = s.cat AND c.kind = s.type
WHERE s.occurred_on <= current_date;

-- Transfers: two legs (-x, +x) sharing a group id. MATERIALIZED so each
-- transfer draws its uuid once, not once per leg.
WITH months AS (
  SELECT m::date AS m
  FROM generate_series(date_trunc('month', current_date) - interval '5 months', current_date, interval '1 month') m
),
t AS MATERIALIZED (
            SELECT gen_random_uuid() AS g, m + 1  AS d, 'Main Checking' AS src, 'Rainy Day' AS dst, 500::numeric AS amt, 'seed: save'         AS descr FROM months
  UNION ALL SELECT gen_random_uuid(),       m + 19,       'Main Checking',        'Visa',             900,                'seed: card payment'          FROM months
  UNION ALL SELECT gen_random_uuid(),       m + 4,        'Main Checking',        'Wallet',           200,                'seed: ATM'                   FROM months
)
INSERT INTO transactions (account_id, amount, type, occurred_on, description, transfer_group_id)
SELECT a.id, leg.amount, 'TRANSFER', t.d, t.descr, t.g
FROM t
CROSS JOIN LATERAL (VALUES (t.src, -t.amt), (t.dst, t.amt)) AS leg (acct, amount)
JOIN accounts a ON a.name = leg.acct
WHERE t.d <= current_date;

COMMIT;
