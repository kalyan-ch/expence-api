# Expence — Bill Reminders on Discord

**Date:** 2026-11-03
**Goal:** Get a Discord message each morning listing the bills that are due soon, due today or overdue, so none are paid late. Reminders stop once a bill is marked Paid on the Bills page.

**Why Discord:** a channel webhook needs no bot, no OAuth and no public endpoint. The API sends one HTTP POST. The Discord phone app turns each message into a push notification.

## Decisions

| Question | Decision | Why |
|---|---|---|
| How to send | Discord **incoming webhook** (Channel settings → Integrations → Webhooks → copy URL) | One POST with JSON. No bot token and nothing for Discord to call back. |
| Where the URL lives | Env var `DISCORD_WEBHOOK_URL`, bound to `app.notify.discord.webhook-url` | Anyone holding the URL can post to the channel, so it stays out of git. If it is blank, notifications are off and the API only logs that. |
| How many messages | **One digest a day**, plus single alerts only for the events in Task 5 and Task 6 | One message a day is easy to read and unlikely to be muted. |
| When | 08:00 local time. `app.notify.cron` (default `0 0 8 * * *`) and `app.notify.zone` (default the system zone) | |
| What counts as due | A month row that is `PENDING`, not `INCOME`, has an amount, and has a billing day | A blank amount means nothing is due. A row with no day can't be dated. |
| Due date | `month.atDay(min(billingDay, month.lengthOfMonth()))` | A billing day of 31 is stored as 31 even in February (see `twiceMonthlyPaycheckLandsOnBothDays`), so it is capped at the month's last day. |
| Lead time | `app.notify.lead-days`, default **3** | |
| Overdue | Listed in every digest until the row is marked Paid, including rows from last month | Repeating it every day is the point of a reminder. |
| "Mark paid" from Discord | **Not in v1.** Each message links to `http://localhost:3000/bills?month=yyyy-MM` instead. | Buttons need a Discord bot and a public HTTPS endpoint for interactions. This is a local, single-user app. |
| Duplicate sends | A `notification_log` table, keyed by `(type, ref)` | A restart or catch-up run never sends the same digest twice. |
| Settings UI | None in v1. Settings come from config, plus a test endpoint. | Lean. Add a settings page only if the defaults turn out to be wrong. |

### The big constraint: the API has to be running

The API and Postgres run on the Mac only when started by hand. A `@Scheduled` job doesn't fire while they're down. The plan handles this in two ways:

1. **Startup catch-up (Task 4):** when the API starts after the send time and today's digest hasn't gone out, it sends the digest then. Opening the app late still gets the reminder.
2. **Keep it up (Task 8):** document a `launchd` agent that starts Docker and the API at login, for anyone who wants reminders without opening the app.

Hosting the API on an always-on box (a Raspberry Pi or a small VPS) is the real fix. It's listed under open questions because it changes how the database is run.

## What a digest looks like

Sent as one Discord embed with one field per section. A section with no rows is left out. If there's nothing to report, nothing is sent.

```
💸 Bills — Tue 3 Nov

🔴 Overdue
  Amex · $412.10 · DCU · was due 1 Nov (2 days)

🟠 Due today
  Rent · $1,850.00 · Chase

🟡 Next 3 days
  Sapphire · $230.55 · Chase · Thu 5 Nov
  Car · $389.00 · DCU · Fri 6 Nov

Chase: $2,080.55 due before next pay (Fri 13 Nov) · forecast left $4,462.09
DCU: $801.10 due · forecast left $1,120.40
→ Open November bills
```

Discord limits: message `content` ≤ 2000 chars, ≤ 10 embeds, embed description ≤ 4096 chars, field value ≤ 1024 chars. Long sections are cut off with "… and N more".

## Status

Tasks 1–4 are done (2026-11-03).
- Built as planned, except: the log column is `ref` rather than `key`, since `key` is a JPQL keyword.
- `RestClient.Builder` needed `spring-boot-starter-restclient`, because Boot 4 moved its auto-configuration out of the web starter.
- The README's Notifications section covers setup.

## Tasks

Each task ships with its tests. API tests use Testcontainers as usual. Discord is never called from tests: `MockRestServiceServer` stands in for it, and a fixed `Clock` bean pins "today".

### Task 1: Discord client and a test endpoint
- `config/NotifyProperties` (`@ConfigurationProperties("app.notify")`): webhookUrl, cron, zone, leadDays.
- `service/DiscordNotifier`: `send(String title, List<Field> fields, String linkUrl)`, built on `RestClient`.
  - Blank webhook URL → log and return.
  - On a 429, wait `retry_after` and retry once. Any other failure is logged and doesn't throw. A reminder must never break a request.
- `POST /api/notifications/test` sends "Expence is connected ✅". It returns 204, or 409 when no webhook is set.
- `application.yml`: `app.notify.*` defaults, with `webhook-url: ${DISCORD_WEBHOOK_URL:}`.
- Add a `Clock` bean (`Clock.system(zone)`) so the later tasks can be tested.

### Task 2: Find the bills that need a reminder
- `service/DueBillService.due(LocalDate today)` returns `DueBills { overdue, today, upcoming }`. Each row holds name, kind, account, amount, due date and month.
- Months to read: last month, this month, and next month when `today + leadDays` crosses into it.
  - Read only months that already exist (`planMonthRepository.findByMonth`). Don't create one here; opening a month is the user's choice.
  - A missing next month means its early bills aren't listed yet. The startup-balance reminder in Task 6 prompts the user to open it.
- Reads each month through the new `MonthService.find(month)`, which returns the month view without creating it. Kind, amount, status and day are filtered in Java, using the due-date cap from the Decisions table.
- Tests:
  - A bill on day 31 is due 28 Feb.
  - Paid, income, blank-amount and no-day rows are left out.
  - An unpaid bill from last month shows as overdue.
  - A lead window across the month end picks up next month's rows only when that month exists.

### Task 3: Build the digest
- `service/BillDigest.build(DueBills, LocalDate today)` turns the rows into a title and fields in the format above. It is pure, with no IO, so it is tested with plain unit tests.
- The per-account footer uses `MonthService`'s existing Remaining forecast. "Due before next pay" sums the unpaid bills dated before the account's next INCOME row (from `daysIn`).
- Money is formatted with the same rules as the UI (`$1,850.00`).
- Tests: empty input gives no message; section order; truncation with "… and N more"; the footer sums.

### Task 4: Schedule it, log it, catch up on startup
- Migration `V20261103100100__create_notification_log.sql`:
  ```sql
  CREATE TABLE notification_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type TEXT NOT NULL,     -- DIGEST, LOW_BALANCE, BALANCES_MISSING
    ref TEXT NOT NULL,      -- e.g. 2026-11-03, or 2026-11:<accountId>
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (type, ref)
  );
  ```
- `service/NotificationService.sendDigest(LocalDate)`: claim the log row first. If the unique key is already taken, someone already sent it, so return. Then build and send. If the send fails, delete the claim so a later run retries.
- `@EnableScheduling`, then `@Scheduled(cron = "${app.notify.cron}", zone = "${app.notify.zone}")` calls `sendDigest(today)`.
- On `ApplicationReadyEvent`: if it's past the send time and there is no DIGEST row for today, send now.
- `POST /api/notifications/digest` sends today's digest on demand, ignoring the log, for checking the output by hand.
- Tests:
  - The second call on the same day sends nothing.
  - The startup catch-up sends after 08:00 and not before.
  - A failed send leaves no log row.

### Task 5: Low-balance alert
- Each day, for each payment account, walk the month day by day: starting balance + pay received − bills due up to that day. If the running balance would go below 0 before the next payday, send one alert.
  - Example: "⚠️ Chase drops to −$212.40 on 12 Nov, the day before pay lands. Move money or delay Sapphire."
- Log ref `yyyy-MM:<accountId>`, so it is sent once per month per account. Send it again only if the shortfall gets bigger.
- This needs the month's starting balance. With no starting balance the check is skipped (Task 6 covers that).

### Task 6: "Enter this month's balances" reminder
- From the 1st, while the current month has payment accounts with no starting balance, the digest gets a line: "Starting balances missing for November: Chase, DCU → enter them".
- This appears inside the digest, not as a separate message, so it adds no extra noise.

### Task 7: Mute reminders per bill (autopay)
- Migration: add `remind BOOLEAN NOT NULL DEFAULT true` to `recurring_items` and `month_items`. It is copied into months the same way as `in_expenditure`.
- DTOs, requests and `apply` gain `remind`. `DueBillService` skips rows where it is false.
- UI (`expence-ui`): a "Remind" checkbox column on Bills and Recurring, next to "In expenditure". A "Remind me" checkbox in the setup wizard's bills step, defaulting to on.
- Tests: a muted bill never appears; the flag copies into new months.

### Task 8: Keep it running, and docs
- README: a Notifications section covering how to create the webhook, export `DISCORD_WEBHOOK_URL`, and `curl -X POST localhost:8081/api/notifications/test`.
- An optional `scripts/launchd/com.expence.api.plist` that runs `docker compose up -d` and `./gradlew bootRun` at login, with install and remove commands. It is documented only; nothing installs it automatically.
- UI: a "Send test notification" button on the Bills setup page that calls the test endpoint, showing whether it worked.

## Order and size

| # | Task | Repo | Size |
|---|---|---|---|
| 1 | Discord client and test endpoint | api | S |
| 2 | Due bill query | api | S |
| 3 | Digest builder | api | S |
| 4 | Schedule, log, catch-up | api | M |
| 6 | Missing-balances line | api | XS |
| 7 | Mute per bill | api and ui | M |
| 5 | Low-balance alert | api | M |
| 8 | Docs, launchd, test button | api and ui | S |

Tasks 1–4 are the minimum useful version: a daily digest from the morning the API is up. Tasks 6 and 7 cut noise. Task 5 is the one that saves money, but it needs balances entered reliably first.

## Open questions

- **Always-on hosting?** Reminders are only as reliable as the Mac being awake with the API running. Moving the API and Postgres to a Pi or VPS (with the UI still local or behind Tailscale) would make the 08:00 send reliable and open the door to Discord buttons later.
- **Discord bot later?** A bot with a slash command (`/paid amex`) or a "Mark paid" button would close the loop from the phone. That needs a public HTTPS endpoint and request signature checks, so it waits on the hosting question.
- **Other channels?** The notifier sits behind one small interface, so email (SMTP), ntfy.sh or Pushover could be added as a second implementation if Discord turns out to be too noisy.
