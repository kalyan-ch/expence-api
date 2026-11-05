# expence-api

The backend for **Expence**, a personal finance tracker that replaces a monthly bills spreadsheet. It plans each month's bills and paychecks per checking account and forecasts what will be left at month end. The UI lives in the separate [`expence-ui`](https://github.com/kalyan-ch/expence-ui) repo.

## Stack

- Java 25, Spring Boot 4.1, Gradle
- PostgreSQL 16, schema owned by Flyway (Hibernate only validates)
- Testcontainers for integration tests
- springdoc OpenAPI

## Quick start

Requires Java 25 and Docker.

```bash
# 1. Database: Postgres 16 on localhost:5434
docker compose up -d

# 2. API: http://localhost:8081 (runs Flyway migrations on start)
./gradlew bootRun
```

The `local` profile is on by default and points at the Compose database (`developer` / `postgresDev`, database `expence`). Set `PORT` to change the API port. CORS allows the UI at `http://localhost:3000`.

API docs: http://localhost:8081/swagger-ui/index.html

## Tests

```bash
./gradlew test
```

Docker must be running: the integration tests start their own throwaway Postgres through Testcontainers and never touch the local database.

## How the bill plan works

The plan mirrors the spreadsheet it replaces:

- **Payment accounts** are the checking accounts bills come out of and paychecks go into. Bills can only be assigned to these accounts.
- **Recurring items** are the template rows, entered once. Each is a bill (expense, card payment or savings) or a paycheck, with a default amount and a schedule:
  - **Monthly:** one fixed day.
  - **Twice a month:** two fixed days.
  - **Every 2 weeks or weekly:** counted from any known payday.
- **Months** are working copies, one per month.
  - **Creating:** opening a month for the first time copies every active recurring item into it, one row per payday.
  - **Filling later:** a month with no rows is filled again when opened. A recurring item added later also joins months already opened, from the current month on.
  - **Editing:** changing a month's rows never changes the template or other months.
- **Starting balances** are typed in per account each month. Each account then gets a forecast: **Remaining = starting balance + pay − bills**.
- **Totals:**
  - **Total** is everything paid out.
  - **Expenditure** includes only bills marked *In expenditure*. That defaults to expenses and card payments; savings and pay don't count.

Amounts are stored as positive numbers; the kind decides the direction. A blank amount means nothing is due.

## Bill reminders on Discord

Each morning at 08:00 the API posts one digest to a Discord channel. It lists:
- Bills that are overdue, due today or due in the next 3 days.
- For each account, what it must cover before the next paycheck.

A bill stops appearing once it is marked Paid. A day with nothing due sends nothing.

1. In Discord, open Channel settings → Integrations → Webhooks, create one and copy its URL.
2. Put the URL in an untracked `.env` (the URL lets anyone post to the channel, so it stays out of git), then start the API:
   ```bash
   cp .env.example .env    # then set DISCORD_WEBHOOK_URL=https://discord.com/api/webhooks/…
   ./gradlew bootRun
   ```
   Spring reads `.env` on start; a real environment variable of the same name overrides it.
3. Check it works:
   ```bash
   curl -X POST localhost:8081/api/notifications/test     # "Expence is connected"
   curl -X POST localhost:8081/api/notifications/digest   # today's digest, now
   ```

Settings live under `app.notify` in `application.yml`:
- `cron`: when the digest goes out.
- `zone`: the time zone, or `NOTIFY_ZONE` in `.env`. It defaults to the machine's own.
- `lead-days`: how far ahead to look.

The digest only goes out while the API is running. If the API starts after the send time, it sends that day's digest straight away. A `notification_log` row makes sure no day is sent twice.


All endpoints are under `/api`. Every month change returns the whole month, because one edit moves the totals.

| Resource | Endpoints |
|---|---|
| Accounts | `GET/POST /accounts`, `GET/PUT/DELETE /accounts/{id}` |
| Recurring items | `GET/POST /recurring-items`, `PUT/DELETE /recurring-items/{id}`. Deleting one used by a month returns 409; retire it with `active: false` instead. |
| Months | `GET /months` (list, for the switcher and trend chart), `GET /months/{yyyy-MM}` (fetch, creating if needed) |
| Month rows | `POST /months/{month}/items`, `PUT/DELETE /months/{month}/items/{id}` |
| Starting balances | `PUT /months/{month}/balances/{accountId}` |
| Notifications | `POST /notifications/test`, `POST /notifications/digest`: 204 when sent, 409 with no webhook set, 502 if Discord refused it |
| Phase 1 ledger | `/categories`, `/transactions`, `/reports/{summary,monthly,by-category}`: built first, not used by the bill plan |

Validation errors return 400 with field errors, missing records 404, and conflicts 409.

## Project layout

```
src/main/java/com/expence/expenceApi/
  controller/   REST endpoints, plus advice/ for the global exception handler
  service/      business rules and month calculations
  dao/          Spring Data repositories
  model/        JPA entities; model/finance/rest/ holds request and response DTOs
src/main/resources/db/migration/   Flyway migrations (V<yyyyMMddHHmmss>__<what>.sql)
scripts/seed-dev.sql               demo transactions for the Phase 1 ledger (local only)
docs/superpowers/                  design specs and implementation plans
```

To load the demo data, run Flyway once (start the API), then:

```bash
docker exec -i expence-postgres psql -p 5434 -U developer -d expence < scripts/seed-dev.sql
```

It only fills the Phase 1 transaction tables, not the bill plan.
