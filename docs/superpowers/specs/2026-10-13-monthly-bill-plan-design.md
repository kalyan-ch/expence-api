# Expence — Monthly Bill Plan (replicating the Excel sheet)

**Date:** 2026-10-13
**Why:** Phase 1 records what already happened. The sheet plans the month ahead: which bills come out of which checking account, when they're due, whether they're paid, and how much will be left after the paychecks arrive. Phase 1 can't do that, so as planned it would not replace the sheet.

## What the sheet does

Left table: one row per monthly bill.
- Columns: No., Category (the bill's name), Amount, From Account (Chase or DCU), Status, Billing day.
- The rows fall into three kinds:
  - Expenses: Rent, India, Car, Misc.
  - Credit card payments: Amex, Sapphire, Freedom, Discover, Platinum.
  - Savings deposits: Citi, Marcus, Discover Savings.
- A blank amount means nothing is due this month.
- Total is the sum of every amount.
- Expenditure is Total minus the savings deposits. It equals Total in the screenshot only because every savings row is blank.

Right table: one row per checking account.
- Balance: typed in from the bank app at the start of the month.
- Salary 1 and Salary 2: the two paychecks each month, each split across both accounts.
- Expenses: the sum of that account's bills from the left table.
- Remaining = Balance + Salary 1 + Salary 2 − Expenses. This is the end-of-month forecast.

Checked against the screenshot:
- Chase Expenses 2425.26 = Rent + Sapphire + Freedom.
- DCU Expenses 1208.02 = Amex + Platinum + Car.
- Chase Remaining 4462.09 = 2915.91 + 1985.72 + 1985.72 − 2425.26. DCU follows the same formula.

## Decisions (answered by the user)

| Question | Answer | Consequence |
|---|---|---|
| Where does Balance come from? | Typed in from the bank app | Store a starting balance per account per month. Don't derive it from transactions. |
| Track every purchase, or only the sheet's bills? | Only the bills | No full ledger. Credit card spending stays on the card; the app tracks bill payments and paychecks. |
| What are Salary 1 and Salary 2? | Two paychecks a month | Store them as income items, each with an account and an amount. One paycheck split across both accounts becomes two items. |
| What do Status and Expenditure mean? | Expenditure leaves out savings | Every item has a kind; Expenditure filters on kind. Status is Pending or Paid. |

## Scope

In scope:
- Recurring items: the bills and paycheck splits, set up once.
- One plan per month, created from the recurring items, where you can edit amounts and status.
- Per-account panel matching the sheet.
- Month switcher, so past months stay viewable.
- CSV export of a month, which covers the backup/export requirement.

Out of scope for now:
- Marking a bill paid does not create a transaction. Nothing would read that transaction, because Balance is typed in by hand.
- Updating the balance mid-month. The forecast assumes Balance was typed at the start of the month, before any bill was paid. A mid-month mode would subtract only unpaid bills; add it if that turns out to be needed.
- Savings account balances (the sheet doesn't track them), reminders and notifications, and importing past months from Excel.

## Data model

Reuse the `accounts` table for Chase and DCU, with type CHECKING. Its opening balance is not used by this feature.
Payment accounts (user request, 2026-10-14): an account flagged `payment_account` is one bills are paid from and paychecks land in. Only flagged accounts appear in bill account dropdowns, and the API rejects a bill on any other account (400). An item keeps its current account if that account is later unflagged.

New tables:

1. **recurring_items**: the template, entered once.
   - name, unique
   - kind: INCOME, EXPENSE, CARD_PAYMENT or SAVINGS
   - default account
   - default amount, optional
   - billing day, optional, 1–31
   - sort order, so the sheet's row order is kept
   - active flag; retiring an item hides it from future months but keeps its history
2. **months**: one row per planned month, stored as the first day of that month, unique.
3. **month_balances**: (month, account), unique. Holds the starting balance typed in from the bank.
4. **month_items**: the month's working copy of the recurring items.
   - month
   - recurring item it came from (empty for a one-off item)
   - name and kind, copied from the template
   - account
   - amount, optional
   - billing day
   - status: PENDING or PAID
   - paid date
   - Changing an amount here never changes the template.

Rules:
- Amounts are stored as positive numbers. The kind decides whether an item adds to or subtracts from the account.
- A billing day past the end of a short month counts as the last day of that month.

## Calculations

Done by the API and returned with each month:

- Outgoing = EXPENSE + CARD_PAYMENT + SAVINGS.
- Total = sum of outgoing amounts. Matches the sheet's Total.
- Expenditure = EXPENSE + CARD_PAYMENT. Matches the sheet's Expenditure.
- Per account:
  - Income = sum of INCOME items, broken out by name so the UI can show Salary 1 and Salary 2 as separate columns.
  - Expenses = sum of outgoing items. Savings deposits count here because they also take money out of checking.
  - Remaining = starting balance + Income − Expenses.
- Also returned: the count and sum of unpaid items, so the page can show what's still due.
- Items with no amount count as zero.

## API

- **Recurring items:** list, create, update, retire. Deleting is allowed only if no month uses the item; otherwise it returns 409.
- **Months:**
  - Fetch one month: items sorted by billing day, starting balances, and every calculated total.
    Fetching a month that doesn't exist yet creates it, copying every active recurring item with its default amount and account (user decision, 2026-10-14; replaces a separate create endpoint).
  - List months that exist, for the switcher.
- **Month items:**
  - Update amount, account, status or billing day.
  - Add a one-off item.
  - Delete an item.
- **Month balances:** set the starting balance for one account in a month.
- **Export:** CSV of one month, or of a date range of months.

## UI

- **Month page (new home page):** same layout as the sheet.
  - Left: the bills table.
    - Amount and account can be edited inline.
    - Status is a Pending/Paid toggle.
    - Unpaid bills past their billing day are highlighted.
    - Total and Expenditure are shown underneath.
  - Right: the account panel.
    - Starting balance can be edited inline.
    - One column per paycheck, plus Expenses and Remaining.
  - Header: month switcher and a "Start next month" button.
- **Recurring items page:** manage the templates and reorder them.
- **Accounts page:** keep as is.
- **Dashboard and reports:** stop being the home page; revisit later (see below).
- **Dashboard: expenditure trend chart (user request, 2026-10-13).**
  - One point per planned month: that month's Expenditure (EXPENSE + CARD_PAYMENT, no savings).
  - Data comes from the plan tables, not transactions. Under bills-only scope the transactions table stays empty, so the existing dashboard charts that read it would show nothing.
  - Needs a months-range endpoint returning Expenditure per month; reuse the month calculations rather than writing a second query.
  - Shape matches the user's sheet graph ("Amount vs Month"): one combined bar per month (user switched from line to bars, 2026-10-13), all months from July 2025 on.
  - Improvements over the sheet graph:
    - Labels carry the year ("Jul '25"); the sheet shows two Julys with no way to tell them apart.
    - The axis stops at the current month; the sheet draws empty future months.
    - The current month is marked as in progress, because its Expenditure is still planned.
    - Hovering a point shows the exact amount.
  - Month naming (user, 2026-10-14): a plan is named for the month the bills are paid (the October sheet).
    Its bills cover the previous month's spending, except Rent, which is the payment month's.
    The chart keeps the sheet's convention and labels each bar with the spending month,
    so the October plan's Expenditure (3,633.28) is the "Sep '26" bar.
    - ponytail: Rent is not split out; it rides in the spending-month bar as the sheet does. Split per-item if the trend ever needs to be exact.
  - History: 15 past months (Jul 2025 – Sep 2026) must be imported or the chart starts with one point.
    - Lazy default: for each past month, create a `months` row with one month item holding that month's total.
      No new schema, and the chart query treats imported and planned months the same way.
    - Full per-bill import only if the sheet keeps each month's bills.

## What happens to existing Phase 1 work

| Item | Decision |
|---|---|
| Accounts API and UI | Keep. Chase and DCU live here. |
| Categories | Not used by the plan. Leave in place; decide later whether to delete. |
| Transactions CRUD, dashboard, reports (built) | Not on this path. Don't delete yet. Delete if still unused once the plan replaces the sheet. |
| Task 7 transfers | Moved to Phase 2/3 (user decision, 2026-10-13). |
| Task 9 CSV import, Task 15 transactions page | Pause. With bills-only scope, nothing needs them. |
| Task 10 CSV export | Repoint to month data. |
| Verification debt (43 API tests that have never run) | Still run first, because Accounts is reused. |

## Tasks

Each task is TDD (failing test, implement, green), with one commit per task, and only after the user approves.

1. Start Docker and run the existing API suite. Fix what breaks, Accounts tests first.
2. API: Flyway migration for the four new tables, plus entities and repositories. Test the constraints against real Postgres: unique month, unique (month, account), billing day range, positive amount.
3. API: recurring items CRUD, including retire and delete-in-use returning 409.
4. API: create month, fetch month, list months. Test that the calculations reproduce the screenshot's numbers exactly, so the sheet itself is the check.
5. API: update and delete month items, add one-off items, set starting balance.
6. UI: API module and query hooks for recurring items and months.
7. UI: Month page, with tests for inline edit, status toggle, overdue highlight and totals.
8. UI: Recurring items page.
9. API and UI: month CSV export.
10. Cutover:
    - Enter the current sheet: 12 bills, 4 paycheck splits, and Chase and DCU balances.
    - Run one real month in the app alongside the sheet.
    - Retire the sheet when they agree.

## Open questions

- Should "Start next month" default each amount to last month's amount instead of the template's? Card statements vary, so the template default may be 0 for cards.
- Do past months from Excel need importing, or is starting fresh fine?
- Do savings deposits need to update a savings account balance? Assumed no.
