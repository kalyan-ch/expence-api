-- ----------------------------------------------------------------------------
-- IN EXPENDITURE
-- Each bill chooses whether it counts toward the month's Expenditure, instead of
-- that being fixed by kind. Existing rows keep the old rule: expenses and card
-- payments count, savings do not.
-- Invariant: a paycheck never counts.
-- ----------------------------------------------------------------------------

ALTER TABLE recurring_items ADD COLUMN in_expenditure BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE month_items     ADD COLUMN in_expenditure BOOLEAN NOT NULL DEFAULT false;

UPDATE recurring_items SET in_expenditure = kind IN ('EXPENSE','CARD_PAYMENT');
UPDATE month_items     SET in_expenditure = kind IN ('EXPENSE','CARD_PAYMENT');

ALTER TABLE recurring_items ADD CONSTRAINT pay_not_expenditure CHECK (kind <> 'INCOME' OR NOT in_expenditure);
ALTER TABLE month_items     ADD CONSTRAINT pay_not_expenditure CHECK (kind <> 'INCOME' OR NOT in_expenditure);
