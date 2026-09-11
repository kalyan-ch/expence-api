# Expence Phase 1 — Core Finance Tracker Design

**Date:** 2026-09-10
**Source features:** `../../../../phase1.md` (repo root `phase1.md`)
**Goal:** Replace a personal-finance Excel spreadsheet with a working local web app: accounts, transactions, transfers, categories, dashboard, reports, CSV import, CSV export.

## Scope

### In scope (Phase 1)

- Accounts: create/edit/delete; types CHECKING, SAVINGS, CREDIT_CARD, CASH; computed balance.
- Transactions: add/edit/delete; filtered and paged list; recent transactions on dashboard.
- Transfers between accounts.
- Categories: seeded defaults plus user-created custom categories; edit/delete.
- Dashboard: total balance, income this month, expenses this month, savings this month, spend-by-category chart, recent transactions.
- Reports: monthly income vs expense over a date range; category totals over a date range.
- CSV import of transactions into a chosen account, with column mapping and duplicate skipping.
- CSV export of transactions over a date range.
- Local Postgres database.

### Out of scope (Phase 2+)

- Budgets (per-category monthly limits). Explicitly deferred.
- Authentication, multi-user, multi-tenancy. Single implicit user; no login.
- Multi-currency and FX. One currency, no currency column.
- Bank sync / aggregators (Plaid etc.).
- Recurring transactions, rules-based auto-categorisation, attachments, mobile app.
- Cloud deployment and CI/CD.
- Soft-delete and audit history.

## Decisions

| Decision | Choice | Why |
|---|---|---|
| Auth | None | Single-user local app; auth is Phase 2. |
| Money | `BigDecimal` / `NUMERIC(19,4)`, single currency | Exact decimal arithmetic without minor-unit conversion at every boundary. |
| Currency | Not stored; UI formats as USD | No FX in Phase 1. Adding a column later is a migration, not a redesign. |
| Schema | Flyway versioned SQL, `hibernate.ddl-auto=validate` | Reviewable, ordered, no drift. |
| Transfers | Two transaction rows sharing `transfer_group_id` | Each account ledger stays complete; balance remains a single `SUM`. |
| Balance | Always computed, never stored | Cannot drift; no cache invalidation or repair job. |
| UI data access | App Router + client-side fetch via TanStack Query | Cache, refetch-on-mutate, loading/error states for free. |
| Build (API) | Gradle 9.1.0 (Groovy DSL), Java 25 toolchain, Spring Boot 4.1.0 | Matches the sibling `culinary-code` project; same versions, same conventions. |
| API code style | Lombok entities/DTOs, layered packages, springdoc-openapi | Follows `culinary-code` so both projects read the same way. |
| Runtime | Postgres in Docker; API and UI run natively | Docker only where a real database is required. |
| Tests | JUnit 5 + Testcontainers, Vitest + Testing Library, Playwright E2E | Full pyramid; integration tests hit real Postgres. |

## Architecture

Two independent repositories, no shared build:

```
expence/
├── phase1.md                  feature list (source of this spec)
├── expence-api/               Spring Boot 4.1.0, Java 25, Gradle 9.1.0, Postgres
└── expence-ui/                Next.js App Router, React, TailwindCSS, TanStack Query
```

Conventions in `expence-api/` follow the sibling project at
`../culinary-code/`: same Spring Boot and Java versions, same Gradle setup, same
layered package shape, same error-body format, same Flyway naming. Divergences
are called out where they occur, each with its reason.

The browser calls the Spring API directly at `NEXT_PUBLIC_API_URL` (default
`http://localhost:8080`). Spring permits CORS from `http://localhost:3000`.
There is no Next.js proxy layer.

```
Browser ──HTTP/JSON──> Spring Boot :8080 ──JDBC──> Postgres :5432
   │
   └── Next.js :3000 serves the app shell (static/RSC), no API proxying
```

### API package layout

Layered, mirroring `culinary-code` (`model/`, `dao/`, `service/`,
`controller/`, `exception/`, `config/`) so the two projects read the same way.
Entities and their REST DTOs live together under `model/finance` and
`model/finance/rest`.

```
com.expence.expenceApi
├── model/finance/       Account, AccountType, Category, CategoryKind,
│                        Transaction, TransactionType
├── model/finance/rest/  AccountDTO, AccountRequest, CategoryDTO, CategoryRequest,
│                        TransactionDTO, TransactionRequest, TransferRequest,
│                        TransferDTO, PageDTO, SummaryDTO, MonthlyPointDTO,
│                        CategoryTotalDTO, ImportRequest, ImportResultDTO,
│                        ImportPreviewDTO
├── dao/                 AccountRepository, CategoryRepository, TransactionRepository,
│                        ReportRepository
├── dao/spec/            TransactionSpecifications (filter composition)
├── service/             AccountService, CategoryService, TransactionService,
│                        TransferService, ReportService, CsvImportService,
│                        CsvExportService
├── controller/          AccountController, CategoryController, TransactionController,
│                        TransferController, ReportController, CsvIoController
├── controller/advice/   GlobalExceptionHandler
├── exception/           NotFoundException, ConflictException
└── config/              CorsConfig
```

Entities use Lombok (`@Getter @Setter @Builder @AllArgsConstructor
@NoArgsConstructor`) with `@GeneratedValue(strategy = GenerationType.UUID)`, and
`created_at` / `updated_at` mapped `insertable = false, updatable = false` — the
database owns them, as in `culinary-code`.

### UI layout

```
expence-ui/
├── app/
│   ├── layout.tsx              shell, nav, QueryClientProvider
│   ├── page.tsx                dashboard
│   ├── accounts/page.tsx
│   ├── transactions/page.tsx
│   ├── categories/page.tsx
│   └── reports/page.tsx
├── lib/
│   ├── api/client.ts           fetch wrapper; {message,errors} -> ApiError
│   ├── api/{accounts,categories,transactions,transfers,reports,csv}.ts
│   ├── hooks/use*.ts           Query/Mutation hooks with invalidation
│   ├── types.ts                shared TS types mirroring API DTOs
│   └── format.ts               money and date formatting
├── components/                 MoneyText, DataTable, Dialog, form fields, charts
└── e2e/                        Playwright specs
```

## Data Model

Migrations live in `src/main/resources/db/migration` and use `culinary-code`'s
timestamped naming, `V<yyyyMMddHHmmss>__<description>.sql`:

- `V20260910100100__create_accounts_table.sql`
- `V20260910100200__create_categories_table.sql`
- `V20260910100300__create_transactions_table.sql`
- `V20260910100400__seed_default_categories.sql`
- `V20260910100500__create_updated_at_trigger.sql`

Combined schema:

```sql
CREATE TABLE accounts (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name            TEXT NOT NULL UNIQUE,
  type            TEXT NOT NULL CHECK (type IN ('CHECKING','SAVINGS','CREDIT_CARD','CASH')),
  opening_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE categories (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name       TEXT NOT NULL,
  kind       TEXT NOT NULL CHECK (kind IN ('INCOME','EXPENSE')),
  is_default BOOLEAN NOT NULL DEFAULT false,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (name, kind)
);

CREATE TABLE transactions (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id        UUID NOT NULL REFERENCES accounts(id),
  category_id       UUID     NULL REFERENCES categories(id),
  amount            NUMERIC(19,4) NOT NULL CHECK (amount <> 0),
  type              TEXT NOT NULL CHECK (type IN ('INCOME','EXPENSE','TRANSFER')),
  occurred_on       DATE NOT NULL,
  description       TEXT,
  transfer_group_id UUID NULL,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT transfer_shape CHECK (
    (type = 'TRANSFER' AND category_id IS NULL     AND transfer_group_id IS NOT NULL) OR
    (type <> 'TRANSFER' AND transfer_group_id IS NULL)
  ),
  CONSTRAINT sign_matches_type CHECK (
    (type = 'INCOME'  AND amount > 0) OR
    (type = 'EXPENSE' AND amount < 0) OR
    (type = 'TRANSFER')
  )
);

CREATE INDEX idx_tx_account_date ON transactions (account_id, occurred_on DESC);
CREATE INDEX idx_tx_date         ON transactions (occurred_on);
CREATE INDEX idx_tx_group        ON transactions (transfer_group_id);
```

`V20260910100400__seed_default_categories.sql` seeds `is_default = true` rows:

- INCOME: Salary, Interest, Other Income
- EXPENSE: Groceries, Rent, Utilities, Transport, Dining, Health, Shopping, Entertainment, Other

`V20260910100500__create_updated_at_trigger.sql` adds a plpgsql
`set_updated_at()` trigger on all three tables, so `updated_at` is maintained by
the database rather than by application code — the same approach `culinary-code`
uses.

### Invariants

1. **Signed amounts.** `amount` carries its own sign: INCOME positive, EXPENSE
   negative, TRANSFER legs negative (source) and positive (destination). No
   query ever needs to branch on type to sum money.
2. **Balance is derived.** `balance(account) = opening_balance + COALESCE(SUM(amount), 0)`
   over that account's transactions. Never stored.
3. **Transfers are not income or expense.** Every report query filters
   `transfer_group_id IS NULL`. Moving money between your own accounts changes
   no totals other than per-account balances.
4. **A transfer is two rows written atomically.** Both legs carry the same
   `transfer_group_id`, the same `occurred_on`, and opposite amounts of equal
   magnitude. Creating or deleting a transfer touches both rows in one database
   transaction.
5. **Categories are typed.** An INCOME transaction may only reference an
   INCOME category; an EXPENSE transaction only an EXPENSE category. Enforced
   in the service layer (the DB cannot express a cross-table check).

### Deletion rules

| Target | Rule |
|---|---|
| Account with transactions | 409 Conflict, message names the transaction count. No cascade. |
| Category in use | 409 Conflict, message names the transaction count. |
| Default category (`is_default = true`) | 409 Conflict; defaults are not deletable, only renameable. |
| Transaction that is a transfer leg | 409 Conflict; delete the transfer via `DELETE /api/transfers/{groupId}`. |
| Transfer | Deletes both legs in one transaction. |

## API

Base path `/api`. JSON in, JSON out. All money fields are JSON numbers with up
to 4 decimal places, serialized from `BigDecimal`.

### Accounts

```
GET    /api/accounts
       200 [{id, name, type, openingBalance, balance}]
POST   /api/accounts          {name, type, openingBalance}      201 AccountResponse
PUT    /api/accounts/{id}     {name, type, openingBalance}      200 AccountResponse
DELETE /api/accounts/{id}                                       204 | 409
```

`balance` is computed per request. `name` is required, 1–100 chars, unique
(duplicate → 409). `type` must be one of the four enum values.

### Categories

```
GET    /api/categories?kind=INCOME|EXPENSE      200 [{id, name, kind, isDefault}]
POST   /api/categories        {name, kind}                      201 CategoryResponse
PUT    /api/categories/{id}   {name}                            200 CategoryResponse
DELETE /api/categories/{id}                                     204 | 409
```

`kind` is immutable after creation. `PUT` renames only.

### Transactions

```
GET /api/transactions?from&to&accountId&categoryId&type&page=0&size=50
    200 {content:[TransactionResponse], page, size, totalElements, totalPages}
    Sorted occurred_on DESC, then created_at DESC.

POST   /api/transactions   {accountId, categoryId, amount, type, occurredOn, description}
       201 TransactionResponse
PUT    /api/transactions/{id}  same body                        200 | 409 (transfer leg)
DELETE /api/transactions/{id}                                   204 | 409 (transfer leg)
```

`TransactionResponse` = `{id, accountId, accountName, categoryId, categoryName,
amount, type, occurredOn, description, transferGroupId}`.

Request `amount` is submitted as a positive magnitude together with `type`; the
service applies the sign (EXPENSE → negated). This keeps the UI form simple and
the invariant in one place.

### Transfers

```
POST   /api/transfers   {fromAccountId, toAccountId, amount, occurredOn, description}
       201 {transferGroupId, fromTransactionId, toTransactionId}
DELETE /api/transfers/{groupId}                                 204 | 404
```

`amount` must be positive. `fromAccountId` must differ from `toAccountId`
(→ 400). Both legs written in one `@Transactional` call.

### Reports

```
GET /api/reports/summary?month=YYYY-MM
    200 {month, totalBalance, income, expenses, savings}
GET /api/reports/monthly?from=YYYY-MM-DD&to=YYYY-MM-DD
    200 [{month:"YYYY-MM", income, expenses}]
GET /api/reports/by-category?from&to&kind=INCOME|EXPENSE
    200 [{categoryId, categoryName, total}]   sorted total DESC
```

- `totalBalance` = sum of every account's computed balance (all time, ignores `month`).
- `income` = `SUM(amount)` where `type='INCOME'` and month matches and `transfer_group_id IS NULL`.
- `expenses` = `ABS(SUM(amount))` where `type='EXPENSE'`, same filters — reported as a positive number.
- `savings` = `income − expenses` (may be negative).
- Months with no activity still appear in `/monthly` with zeros, so charts have no gaps.

### CSV import

```
POST /api/import/transactions   multipart/form-data
     file:        the CSV
     accountId:   target account
     dateColumn, amountColumn, descriptionColumn:  header names from the file
     dateFormat:  e.g. "yyyy-MM-dd" or "MM/dd/yyyy"
     200 {imported, skipped, errors:[{row, message}]}

POST /api/import/preview        multipart/form-data (file only)
     200 {headers:[...], sampleRows:[[...]]}   first 5 data rows
```

Behaviour:

- Sign of the parsed amount decides `type`: positive → INCOME, negative → EXPENSE.
- `categoryId` is left NULL; the user categorises afterwards in the UI.
- A row is **skipped** (not an error) when a transaction already exists with the
  same `account_id`, `occurred_on`, `amount` and `description`.
- A row that fails to parse is reported in `errors` with its 1-based row number;
  the remaining valid rows still commit.
- Zero-amount rows are errors (the DB forbids `amount = 0`).
- Parser: Apache Commons CSV, header-aware.

### CSV export

```
GET /api/export/transactions.csv?from=YYYY-MM-DD&to=YYYY-MM-DD
    200 text/csv
    Content-Disposition: attachment; filename="transactions-<from>-<to>.csv"
    Header: date,account,category,type,amount,description
```

Transfers are included, with an empty category and their signed amount, so the
export reconciles against account balances.

### Error format

A single `GlobalExceptionHandler` (`@RestControllerAdvice`) produces
`culinary-code`'s error shape — `{"message": ...}`, plus an `errors` map of
field name to message for validation failures:

```json
{"message": "Account 'Checking' has 42 transactions and cannot be deleted"}
```

```json
{"message": "Validation failed",
 "errors": {"name": "must not be blank", "amount": "must be greater than 0"}}
```

| Condition | Status |
|---|---|
| `MethodArgumentNotValidException` | 400, with the `errors` map |
| Malformed body, bad UUID, missing parameter | 400 |
| `DataIntegrityViolationException` | 400, generic message |
| `NotFoundException` | 404 |
| `ConflictException` — unique-name clash, delete blocked by references, transfer-leg edit | 409 |
| Anything else | 500, logged, message suppressed |

Framework client-error exceptions are handled explicitly, for the reason
documented in `culinary-code`: handlers on a `@RestControllerAdvice` run before
Spring's `DefaultHandlerExceptionResolver`, so without them the catch-all
reports a malformed UUID as a 500.

## UI

### Pages

**Dashboard (`/`)** — four stat tiles (total balance, income this month,
expenses this month, savings this month), a spend-by-category donut for the
current month, and the ten most recent transactions. Month selector defaults to
the current month.

**Accounts (`/accounts`)** — table of name, type, opening balance, current
balance. Create/edit in a dialog. Delete asks for confirmation and surfaces the
409 message when the account has transactions.

**Transactions (`/transactions`)** — filter bar (date range, account, category,
type), paged table, "Add transaction" drawer, "Transfer" dialog, "Import CSV"
flow, "Export CSV" button. Editing a transfer leg is blocked in the UI with an
explanation, matching the API rule.

**Categories (`/categories`)** — two lists (income, expense). Defaults are
marked and cannot be deleted. Add and rename inline.

**Reports (`/reports`)** — date-range picker, monthly income-vs-expense grouped
bars, and a category-totals table with each category's share of the total.

### Data access

`lib/api/client.ts` wraps `fetch`: prefixes `NEXT_PUBLIC_API_URL`, sets JSON
headers, and on a non-2xx response parses the `{message, errors?}` body into an
`ApiError` carrying `status`, `message` and an optional `fieldErrors` map. Forms
render `fieldErrors` next to the offending inputs. Every module in `lib/api/` is
a thin typed function over the client.

`lib/hooks/` exposes one hook per operation. Mutations invalidate the query keys
they affect — creating a transaction invalidates `['transactions']`,
`['accounts']` (balances moved) and `['reports']`. This is why the dashboard is
correct the moment an edit lands.

Charts use Recharts.

### Formatting

`lib/format.ts` holds `formatMoney(value: number): string` (2 decimals, grouping
separators, negative in parentheses) and `formatDate(iso: string): string`.
Nothing else formats money inline.

## Testing

**API — JUnit 5, Testcontainers Postgres.** Repositories under `@DataJpaTest`
against a container; services and controllers under `@SpringBootTest` with
MockMvc. No mocked repositories: the balance and report SQL is the thing most
worth testing, and mocks cannot test SQL. A shared `AbstractIntegrationTest`
holds a singleton container (`@ServiceConnection`) so the suite starts one
database, and Flyway runs against it so the migrations are exercised too.

This is a deliberate divergence from `culinary-code`, which tests on H2 with
Flyway disabled. That project's own test config records the trade-off — "a
mistake in a migration file will not be caught here… verifying those needs a
real Postgres (Testcontainers)". Expence's schema leans on `gen_random_uuid()`,
a plpgsql trigger and multi-column `CHECK` constraints that H2 cannot reproduce,
and its correctness rests on SQL aggregates, so it takes the Testcontainers
route from the start.

Required cases include: computed balance with and without transactions; a
transfer writing both legs; reports excluding transfers; deleting an account
with transactions returning 409; import skipping duplicates; import reporting a
bad row while committing good ones; export round-tripping the amounts.

**UI — Vitest + Testing Library, MSW for API stubs.** Covers `formatMoney`
edge cases (zero, negative, thousands), the transaction form's validation, and
the query hooks' invalidation behaviour.

**E2E — Playwright** against the real stack (Docker Postgres, `bootRun`,
`next dev`), four specs:

1. Create account → add income and expense → dashboard tiles and donut update.
2. Transfer between two accounts → both balances move, income/expense unchanged.
3. Import a CSV → rows appear; re-import the same file → all rows skipped.
4. Export CSV → file downloads with the expected header and row count.

## Local Development

Postgres runs in Docker; everything else runs natively.

```bash
# database (expence-api/docker-compose.yml — database only)
docker compose up -d

# api  (http://localhost:8080)
./gradlew bootRun

# ui   (http://localhost:3000)
npm run dev
```

Configuration follows `culinary-code`'s split: `application.yml` holds what is
true everywhere (`spring.application.name: expenceApi`, `ddl-auto: validate`,
`server.port: ${PORT:8080}`, `profiles.active: local`), and
`application-local.yml` holds the local datasource
(`jdbc:postgresql://localhost:5432/expence`), Flyway locations, and
`app.cors.allowed-origins: http://localhost:3000`. No `application-prod.yml` in
Phase 1 — there is no deployment target yet.

`expence-ui/.env.local` sets `NEXT_PUBLIC_API_URL=http://localhost:8080`.

Testcontainers reuses the same Docker daemon and starts its own throwaway
container per test run, so the dev database is never touched by tests.

## Build Tooling

The API build mirrors `culinary-code` exactly where it can:

- Gradle 9.1.0 wrapper, Groovy `build.gradle`, `settings.gradle` with
  `rootProject.name = 'expenceApi'`.
- `org.springframework.boot` 4.1.0, `io.spring.dependency-management` 1.1.7.
- Java toolchain 25.
- Dependencies: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`,
  `spring-boot-starter-validation`, `spring-boot-starter-flyway`,
  `org.flywaydb:flyway-database-postgresql`, `org.postgresql:postgresql`,
  `org.apache.commons:commons-csv`,
  `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0`,
  Lombok 1.18.42 (`compileOnly` + `annotationProcessor`).
- Test dependencies: `spring-boot-starter-test`,
  `spring-boot-testcontainers`, `org.testcontainers:postgresql`,
  `org.testcontainers:junit-jupiter`,
  `org.junit.platform:junit-platform-launcher` (runtime).
- No `spring-boot-starter-security` and no H2: Phase 1 has no auth, and tests
  run on Testcontainers Postgres.

`expence-api/` currently contains a `.mvn/` directory from an abandoned Maven
scaffold. Implementation removes `.mvn/` (and `mvnw`, `mvnw.cmd` if present)
before initialising the Gradle wrapper. Both repositories are otherwise empty
apart from `.gitignore`.
