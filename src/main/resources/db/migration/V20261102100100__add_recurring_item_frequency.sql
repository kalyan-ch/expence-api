-- ----------------------------------------------------------------------------
-- PAY FREQUENCY
-- A recurring item (in practice a paycheck) can land more than once a month.
-- Invariants:
--   MONTHLY uses billing_day only.
--   TWICE_MONTHLY uses billing_day and a different second_day.
--   BIWEEKLY and WEEKLY count from anchor_date (a known payday) and use no fixed day.
--   a recurring item is copied into a month at most once per day, so a
--   biweekly paycheck can appear two or three times.
-- ----------------------------------------------------------------------------

ALTER TABLE recurring_items
  ADD COLUMN frequency   TEXT NOT NULL DEFAULT 'MONTHLY'
                         CHECK (frequency IN ('MONTHLY','TWICE_MONTHLY','BIWEEKLY','WEEKLY')),
  ADD COLUMN second_day  INT  NULL CHECK (second_day BETWEEN 1 AND 31),
  ADD COLUMN anchor_date DATE NULL,
  ADD CONSTRAINT frequency_shape CHECK (
       (frequency = 'MONTHLY' AND second_day IS NULL AND anchor_date IS NULL)
    OR (frequency = 'TWICE_MONTHLY' AND billing_day IS NOT NULL AND second_day IS NOT NULL
        AND second_day <> billing_day AND anchor_date IS NULL)
    OR (frequency IN ('BIWEEKLY','WEEKLY') AND anchor_date IS NOT NULL
        AND billing_day IS NULL AND second_day IS NULL));

ALTER TABLE month_items DROP CONSTRAINT month_items_month_id_recurring_item_id_key;
-- One-off rows (no recurring item) are not limited.
CREATE UNIQUE INDEX month_items_once_per_day
  ON month_items (month_id, recurring_item_id, COALESCE(billing_day, 0))
  WHERE recurring_item_id IS NOT NULL;
