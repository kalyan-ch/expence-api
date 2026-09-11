# Expence Phase 1 — Core Finance Tracker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a local, single-user personal finance tracker — accounts, transactions, transfers, categories, dashboard, reports, CSV import and CSV export — that replaces a spreadsheet.

**Architecture:** Two independent repositories. `expence-api` is a Spring Boot 4.1.0 / Java 25 / Gradle service over Postgres, laid out in layered packages (`model`, `dao`, `service`, `controller`) matching the sibling `culinary-code` project. `expence-ui` is a Next.js App Router front end that calls the API directly from the browser through TanStack Query. Money is `BigDecimal`/`NUMERIC(19,4)` with signed amounts; account balances are always computed, never stored; a transfer is two linked transaction rows.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Gradle 9.1.0 (Groovy DSL), Spring Data JPA, Flyway, Postgres 16, Apache Commons CSV, Lombok, springdoc-openapi, JUnit 5, Testcontainers · Next.js 15 (App Router), React 19, TypeScript, TailwindCSS 4, TanStack Query 5, Recharts, Vitest, Testing Library, MSW, Playwright.

**Spec:** `expence-api/docs/superpowers/specs/2026-09-10-phase1-core-finance-tracker-design.md` — read it before starting any task. It holds the full schema DDL, endpoint contracts, error shape and UI page descriptions that this plan refers to rather than repeats.

**Reference project:** `/Users/kalyanchavali/workspace/culinary-code` — read its `build.gradle`, `src/main/resources/application*.yml`, an entity under `src/main/java/com/wb/culinaryCode/model/`, and `controller/advice/GlobalExceptionHandler.java` before Task 1. Expence follows its conventions.

## Global Constraints

- Java toolchain **25**; Spring Boot plugin **4.1.0**; dependency-management plugin **1.1.7**; Gradle wrapper **9.1.0**.
- Gradle build is **Groovy** (`build.gradle`), not Kotlin DSL. `rootProject.name = 'expenceApi'`.
- Java base package **`com.expence.expenceApi`**. Layered packages only: `model/finance`, `model/finance/rest`, `dao`, `dao/spec`, `service`, `controller`, `controller/advice`, `exception`, `config`.
- Entities use Lombok `@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor`, `@GeneratedValue(strategy = GenerationType.UUID)`, and map `created_at`/`updated_at` as `insertable = false, updatable = false`.
- `spring.jpa.hibernate.ddl-auto: validate`. Schema changes happen **only** through Flyway.
- Flyway filenames: `V<yyyyMMddHHmmss>__<snake_case_description>.sql` in `src/main/resources/db/migration`.
- Money: `BigDecimal` / `NUMERIC(19,4)`. Never `double` or `float`. Single currency, no currency column.
- Signed amounts: `INCOME` rows `amount > 0`, `EXPENSE` rows `amount < 0`, `TRANSFER` legs `-x` and `+x`.
- Account balance = `opening_balance + COALESCE(SUM(amount), 0)`. **Never stored.**
- Every income/expense/report aggregate filters `transfer_group_id IS NULL`.
- API error body is `{"message": "..."}`, plus `{"errors": {"field": "msg"}}` for validation failures. **Not** RFC 7807.
- API base path `/api`; API port **8080**; UI port **3000**; CORS allows `http://localhost:3000` only.
- Postgres runs in Docker (`expence-api/docker-compose.yml`, database only). API and UI run natively.
- API tests run on **Testcontainers Postgres with Flyway enabled**. No H2, no mocked repositories in integration tests.
- No authentication, no budgets, no multi-currency, no bank sync in Phase 1.
- TDD throughout: write the failing test, watch it fail, implement, watch it pass, commit.

## File Structure

**`expence-api/`**

| Path | Responsibility |
|---|---|
| `build.gradle`, `settings.gradle`, `gradle/wrapper/` | Build definition and wrapper |
| `docker-compose.yml` | Postgres 16 for local development |
| `src/main/resources/application.yml` | Settings true in every profile |
| `src/main/resources/application-local.yml` | Local datasource, Flyway, CORS origin |
| `src/main/resources/db/migration/*.sql` | Versioned schema and seed data |
| `model/finance/*.java` | JPA entities and enums |
| `model/finance/rest/*.java` | Request and response DTOs |
| `dao/*.java` | Spring Data repositories and report projections |
| `dao/spec/TransactionSpecifications.java` | Composable transaction filters |
| `service/*.java` | Business rules: balances, transfer legs, report shaping, CSV |
| `controller/*.java` | HTTP mapping and validation entry points |
| `controller/advice/GlobalExceptionHandler.java` | Exception → `{"message":...}` |
| `exception/*.java` | `NotFoundException`, `ConflictException` |
| `config/CorsConfig.java` | CORS from `app.cors.allowed-origins` |
| `src/test/java/.../AbstractIntegrationTest.java` | Shared Testcontainers Postgres |

**`expence-ui/`**

| Path | Responsibility |
|---|---|
| `app/layout.tsx` | Shell, navigation, `QueryClientProvider` |
| `app/page.tsx` | Dashboard |
| `app/accounts/`, `app/categories/`, `app/transactions/`, `app/reports/` | Feature pages |
| `lib/types.ts` | TypeScript mirrors of API DTOs |
| `lib/api/client.ts` | `fetch` wrapper and `ApiError` |
| `lib/api/*.ts` | One typed module per API area |
| `lib/hooks/*.ts` | TanStack Query hooks with invalidation |
| `lib/format.ts` | `formatMoney`, `formatDate` |
| `components/*.tsx` | Reusable UI: table, dialog, money text, charts |
| `e2e/*.spec.ts` | Playwright specs against the real stack |

---

## Task 1: API scaffolding, Postgres, and a green build

**Files:**
- Delete: `expence-api/.mvn/`, `mvnw`, `mvnw.cmd`
- Create: `build.gradle`, `settings.gradle`, `gradle/wrapper/`, `gradlew`, `gradlew.bat`, `docker-compose.yml`
- Create: `ExpenceApiApplication.java`, `application.yml`, `application-local.yml`, `config/CorsConfig.java`
- Test: `AbstractIntegrationTest.java`, `ExpenceApiApplicationTests.java`, `src/test/resources/application-test.yml`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: `AbstractIntegrationTest` — abstract JUnit base starting a singleton Postgres container wired by `@ServiceConnection`, with Flyway enabled; every later integration test extends it. Also the Gradle build and the `local` profile.

- [ ] **Step 1: Remove the abandoned Maven scaffold** — `rm -rf .mvn mvnw mvnw.cmd`.
- [ ] **Step 2: Install the Gradle wrapper at 9.1.0** — copy `gradle/`, `gradlew`, `gradlew.bat` from `../culinary-code`; confirm `distributionUrl` names `gradle-9.1.0-bin.zip`.
- [ ] **Step 3: Write `settings.gradle` and `build.gradle`** — versions and the dependency list from the spec's *Build Tooling* section, copied from `culinary-code`'s `build.gradle` where they match.
- [ ] **Step 4: Write `docker-compose.yml`** — Postgres 16 only, database `expence`, user `developer`, port 5432, named volume.
- [ ] **Step 5: Start Postgres** — `docker compose up -d`, then `docker exec expence-postgres pg_isready -U developer -d expence`. Expected: `accepting connections`.
- [ ] **Step 6: Write the entry point and the two YAMLs** — split as the spec's *Local Development* section describes: `application.yml` for what is true everywhere, `application-local.yml` for the datasource, Flyway locations and `app.cors.allowed-origins`.
- [ ] **Step 7: Write `CorsConfig`** — `WebMvcConfigurer` mapping `/api/**` to the origins in `app.cors.allowed-origins`, methods GET/POST/PUT/DELETE/OPTIONS.
- [ ] **Step 8: Write `AbstractIntegrationTest`** — one static `PostgreSQLContainer` started in a static initialiser (not `@Testcontainers`, so JUnit never stops it between classes), `@ServiceConnection`, `@ActiveProfiles("test")`, Flyway forced on via `@DynamicPropertySource`. Comment why the container is a singleton. Add `application-test.yml` holding only what must differ from local.
- [ ] **Step 9: Write the context-loads test** — `ExpenceApiApplicationTests extends AbstractIntegrationTest` with an empty `contextLoads`.
- [ ] **Step 10: Run `./gradlew test`** — Expected: FAIL. There are no migrations and `ddl-auto: validate` has nothing to validate. Task 2 makes it green.
- [ ] **Step 11: Create `src/main/resources/db/migration/`** then run `./gradlew compileJava`. Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 12: Commit** — `chore: scaffold Spring Boot 4.1 API with Gradle, Postgres, Testcontainers`, including the spec and this plan.

---

## Task 2: Schema, entities, and repositories

**Files:**
- Create: five migrations — accounts, categories, transactions, seed categories, `updated_at` trigger (names and DDL in the spec's *Data Model*)
- Create: `model/finance/` — `AccountType`, `CategoryKind`, `TransactionType`, `Account`, `Category`, `Transaction`
- Create: `dao/` — `AccountRepository`, `CategoryRepository`, `TransactionRepository`, `AccountBalanceRow`
- Test: `dao/SchemaAndRepositoryTest.java`

**Interfaces:**
- Consumes: `AbstractIntegrationTest`.
- Produces:
  - `Account(UUID id, String name, AccountType type, BigDecimal openingBalance, Instant createdAt, Instant updatedAt)`
  - `Category(UUID id, String name, CategoryKind kind, boolean isDefault, Instant createdAt, Instant updatedAt)`
  - `Transaction(UUID id, Account account, Category category, BigDecimal amount, TransactionType type, LocalDate occurredOn, String description, UUID transferGroupId, Instant createdAt, Instant updatedAt)` — `account` and `category` are `@ManyToOne(fetch = EAGER)` because every read path maps to a DTO carrying both names
  - `AccountRepository extends JpaRepository<Account, UUID>`: `existsByNameIgnoreCase(String)`, `existsByNameIgnoreCaseAndIdNot(String, UUID)`
  - `CategoryRepository extends JpaRepository<Category, UUID>`: `findAllByKindOrderByNameAsc(CategoryKind)`, `findAllByOrderByKindAscNameAsc()`, `existsByNameIgnoreCaseAndKind(String, CategoryKind)`, `existsByNameIgnoreCaseAndKindAndIdNot(String, CategoryKind, UUID)`
  - `TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction>`: `sumByAccountId(UUID): BigDecimal`, `sumGroupedByAccount(): List<AccountBalanceRow>`, `countByAccountId(UUID)`, `countByCategoryId(UUID)`, `findAllByTransferGroupId(UUID)`, `existsByAccountIdAndOccurredOnAndAmountAndDescription(UUID, LocalDate, BigDecimal, String)`
  - `AccountBalanceRow` projection: `getAccountId(): UUID`, `getTotal(): BigDecimal`

- [ ] **Step 1: Write the five migrations** — DDL verbatim from the spec's *Data Model*: the three tables with their `CHECK` constraints (`amount <> 0`, `transfer_shape`, `sign_matches_type`), the three indexes, the twelve seeded `is_default` categories, and the plpgsql `set_updated_at()` trigger on all three tables. Head each file with a comment saying what invariant it enforces.
- [ ] **Step 2: Write the failing `SchemaAndRepositoryTest`** — cases: seeds 3 income and 9 expense defaults; sums transactions per account; sum is zero for an untouched account; rejects a positive EXPENSE; rejects a TRANSFER leg with no group id; rejects a zero amount; finds both legs by group id; detects a duplicate row for import; detects a duplicate account name case-insensitively. Constraint violations assert `DataIntegrityViolationException` and must use `saveAndFlush`.
- [ ] **Step 3: Run `./gradlew test --tests '*SchemaAndRepositoryTest'`** — Expected: FAIL, `package com.expence.expenceApi.model.finance does not exist`.
- [ ] **Step 4: Write the three enums and three entities** — per the Interfaces block; comment the `EAGER` choice and the derived-balance rule.
- [ ] **Step 5: Write the three repositories and `AccountBalanceRow`** — `sumByAccountId` and `sumGroupedByAccount` are JPQL with `coalesce(sum(...), 0)`; the grouped query exists so listing accounts is not N+1.
- [ ] **Step 6: Run the test** — Expected: PASS, 9 tests. If the sign test passes but the zero-amount test fails, the `amount <> 0` CHECK is missing.
- [ ] **Step 7: Verify migrations against the dev database** — `docker compose up -d`, `./gradlew bootRun`, then `psql \dt` and `SELECT name, kind FROM categories`. Expected: three tables plus `flyway_schema_history`, 12 categories.
- [ ] **Step 8: Commit** — `feat: add finance schema, entities and repositories`.

---

## Task 3: Shared exceptions and the global exception handler

**Files:**
- Create: `exception/NotFoundException.java`, `exception/ConflictException.java`, `controller/advice/GlobalExceptionHandler.java`
- Test: `controller/advice/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: `AbstractIntegrationTest`.
- Produces: `NotFoundException(String)` → 404; `ConflictException(String)` → 409; body `{"message": ...}` plus `{"errors": {field: message}}` on validation failure.

- [ ] **Step 1: Write the failing test** — a `@TestConfiguration` controller that throws each exception from a `/api/test/...` route; assert 404 and 409 with the thrown message, and that a malformed path UUID gives 400 rather than 500.
- [ ] **Step 2: Run it** — Expected: FAIL, `package com.expence.expenceApi.exception does not exist`.
- [ ] **Step 3: Write the two exceptions** — both extend `RuntimeException`; javadoc `ConflictException` with its three uses (duplicate name, delete blocked by references, transfer-leg edit).
- [ ] **Step 4: Write `GlobalExceptionHandler`** — `@RestControllerAdvice` covering the status table in the spec's *Error format*: `MethodArgumentNotValidException` → 400 with the field map; `MethodArgumentTypeMismatchException`, `HttpMessageNotReadableException`, `MissingServletRequestParameterException`, `IllegalArgumentException`, `DataIntegrityViolationException` → 400; `HttpRequestMethodNotSupportedException` → 405; `NotFoundException`/`NoResourceFoundException` → 404; `ConflictException` → 409; `Exception` → 500 logged with the message suppressed. Comment why the framework exceptions are handled explicitly: advice handlers run before `DefaultHandlerExceptionResolver`, so without them the catch-all reports a bad UUID as a 500.
- [ ] **Step 5: Run the test** — Expected: PASS, 3 tests.
- [ ] **Step 6: Commit** — `feat: add global exception handler with message error body`.

---

## Task 4: Accounts CRUD with computed balances

**Files:**
- Create: `model/finance/rest/AccountRequest.java`, `AccountDTO.java`, `service/AccountService.java`, `controller/AccountController.java`
- Test: `controller/AccountControllerTest.java`

**Interfaces:**
- Consumes: `Account`, `AccountType`, `AccountRepository`, `TransactionRepository.{sumGroupedByAccount, sumByAccountId, countByAccountId}`, `NotFoundException`, `ConflictException`.
- Produces:
  - `AccountRequest(String name, AccountType type, BigDecimal openingBalance)` — `@NotBlank @Size(max=100)`, `@NotNull`, `@NotNull @Digits(integer=15, fraction=4)`
  - `AccountDTO(UUID id, String name, AccountType type, BigDecimal openingBalance, BigDecimal balance)`
  - `AccountService`: `list(): List<AccountDTO>`, `get(UUID)`, `require(UUID): Account`, `totalBalance(): BigDecimal`, `create(AccountRequest)`, `update(UUID, AccountRequest)`, `delete(UUID)`
  - `GET/POST /api/accounts`, `GET/PUT/DELETE /api/accounts/{id}`

- [ ] **Step 1: Write the failing `AccountControllerTest`** — cases: create returns 201 with `balance == openingBalance`; balance is opening plus the transaction sum; an account with no activity lists at its opening balance; blank name → 400 with `errors.name`; duplicate name (differing only in case) → 409 `An account named 'checking' already exists`; update changes name/type/opening balance; keeping its own name on update is not a duplicate; delete of an empty account → 204; delete with transactions → 409 `Account 'Busy' has 1 transaction and cannot be deleted` (singular/plural); unknown id → 404 `Account not found`. Clear `transactions` then `accounts` in `@BeforeEach`.
- [ ] **Step 2: Run it** — Expected: FAIL, every request 404s; no controller.
- [ ] **Step 3: Write the DTOs** — validation annotations as in the Interfaces block; javadoc `balance` as derived per request.
- [ ] **Step 4: Write `AccountService`** — `list()` reads every account's total in the one grouped query and sorts case-insensitively by name; `get`/`update` use `sumByAccountId`; `require` throws `NotFoundException`; `create`/`update` check the name with the `IgnoreCase` finders; `delete` refuses when `countByAccountId > 0`; `totalBalance()` sums `list()` for the dashboard tile. Trim names on write. Read methods `@Transactional(readOnly = true)`.
- [ ] **Step 5: Write `AccountController`** — thin mapping, `@Valid` on both bodies, 201 on create, 204 on delete.
- [ ] **Step 6: Run the test** — Expected: PASS, 10 tests.
- [ ] **Step 7: Commit** — `feat: add account CRUD with computed balances`.

---

## Task 5: Categories CRUD

**Files:**
- Create: `model/finance/rest/CategoryRequest.java`, `CategoryDTO.java`, `service/CategoryService.java`, `controller/CategoryController.java`
- Test: `controller/CategoryControllerTest.java`

**Interfaces:**
- Consumes: `Category`, `CategoryKind`, `CategoryRepository`, `TransactionRepository.countByCategoryId(UUID)`, `NotFoundException`, `ConflictException`.
- Produces:
  - `CategoryRequest(String name, CategoryKind kind)` — `kind` is required at creation and **ignored on update**
  - `CategoryDTO(UUID id, String name, CategoryKind kind, boolean isDefault)` — annotate the flag `@JsonProperty("isDefault")`, or Jackson serialises it as `default`
  - `CategoryService`: `list(CategoryKind kindOrNull): List<CategoryDTO>`, `require(UUID): Category`, `create(CategoryRequest)`, `rename(UUID, CategoryRequest)`, `delete(UUID)`
  - `GET/POST /api/categories`, `PUT/DELETE /api/categories/{id}`

- [ ] **Step 1: Write the failing `CategoryControllerTest`** — cases: lists all; filters by `kind`; creates a custom category with `isDefault:false`; duplicate name within a kind → 409 `An expense category named 'groceries' already exists`; the same name in the other kind is allowed; rename does not change kind even when the body says otherwise; delete of an unused custom category → 204; delete of a default → 409 `'Groceries' is a default category and cannot be deleted`; delete of a category in use → 409 `Category 'Pets' is used by 1 transaction and cannot be deleted`; unknown id → 404 `Category not found`. Seed a known two-category set in `@BeforeEach` rather than relying on the Flyway seed.
- [ ] **Step 2: Run it** — Expected: FAIL, no controller.
- [ ] **Step 3: Write the DTOs** — `@NotBlank @Size(max=60)` name; javadoc that `kind` is immutable after creation.
- [ ] **Step 4: Write `CategoryService`** — `list(null)` returns `findAllByOrderByKindAscNameAsc()`; `create` rejects a duplicate within the kind; `rename` reuses the `...AndIdNot` finder and never touches `kind`; `delete` checks `isDefault` first, then `countByCategoryId`.
- [ ] **Step 5: Write `CategoryController`** — `kind` as an optional `@RequestParam`.
- [ ] **Step 6: Run the test** — Expected: PASS, 10 tests.
- [ ] **Step 7: Commit** — `feat: add category CRUD with default-category protection`.

---

## Task 6: Transactions CRUD, filtering and paging

**Files:**
- Create: `model/finance/rest/TransactionRequest.java`, `TransactionDTO.java`, `PageDTO.java`, `dao/spec/TransactionSpecifications.java`, `service/TransactionService.java`, `controller/TransactionController.java`
- Test: `controller/TransactionControllerTest.java`

**Interfaces:**
- Consumes: `AccountService.require(UUID)`, `CategoryService.require(UUID)`, `TransactionRepository`, `NotFoundException`, `ConflictException`.
- Produces:
  - `TransactionRequest(UUID accountId, UUID categoryId, BigDecimal amount, TransactionType type, LocalDate occurredOn, String description)` — `amount` is a **positive magnitude** (`@Positive`); `categoryId` optional; the service applies the sign
  - `TransactionDTO(UUID id, UUID accountId, String accountName, UUID categoryId, String categoryName, BigDecimal amount, TransactionType type, LocalDate occurredOn, String description, UUID transferGroupId)` — `amount` signed as stored
  - `PageDTO<T>(List<T> content, int page, int size, long totalElements, int totalPages)` with `static <E,D> PageDTO<D> of(Page<E>, Function<E,D>)`
  - `TransactionSpecifications.filter(LocalDate from, LocalDate to, UUID accountId, UUID categoryId, TransactionType type): Specification<Transaction>` — every argument optional, nulls drop out
  - `TransactionService`: `list(from, to, accountId, categoryId, type, page, size): PageDTO<TransactionDTO>`, `recent(int limit): List<TransactionDTO>`, `require(UUID): Transaction`, `create`, `update`, `delete`, `toDto(Transaction): TransactionDTO`
  - `GET/POST /api/transactions`, `PUT/DELETE /api/transactions/{id}`

- [ ] **Step 1: Write the failing `TransactionControllerTest`** — cases: an EXPENSE is stored negative and the DTO carries account and category names; INCOME stays positive; a negative magnitude → 400 with `errors.amount`; INCOME with an EXPENSE category → 409 `Category 'Groceries' is an expense category and cannot be used on an income transaction`; `type=TRANSFER` → 400 `Use POST /api/transfers to move money between accounts`; unknown account → 404; list is newest-first with correct `page`/`size`/`totalElements`/`totalPages`; each of the four filters narrows the result; update reapplies the sign and can move the row to another account; delete → 204; editing or deleting a transfer leg → 409 `This transaction is part of a transfer; edit or delete the transfer instead`.
- [ ] **Step 2: Run it** — Expected: FAIL, no controller.
- [ ] **Step 3: Write `PageDTO`** — our own envelope rather than serialising Spring's `Page`, whose JSON shape is unstable across versions; say so in a javadoc.
- [ ] **Step 4: Write the transaction DTOs** — javadoc that the request amount is a positive magnitude and that `transferGroupId` is how the UI knows to block inline editing.
- [ ] **Step 5: Write `TransactionSpecifications`** — final class, private constructor, predicates collected in a list and `and`-ed.
- [ ] **Step 6: Write `TransactionService`** — sort constant `occurredOn DESC, createdAt DESC`; one private `signed(request)` that negates for EXPENSE (the only place the sign convention lives); one private `resolveCategory` enforcing kind-matches-type; reject `TRANSFER` with `IllegalArgumentException` so the handler maps it to 400; `update`/`delete` refuse rows with a `transferGroupId`; `recent(limit)` reuses the sort for the dashboard.
- [ ] **Step 7: Write `TransactionController`** — `@DateTimeFormat(iso = DATE)` on `from`/`to`, defaults `page=0`, `size=50`, and clamp `size` to 200.
- [ ] **Step 8: Run the test** — Expected: PASS, 11 tests. A 500 on the TRANSFER case means Task 3's `IllegalArgumentException` handler is missing.
- [ ] **Step 9: Commit** — `feat: add transaction CRUD with filtering and paging`.

---

## Task 7: Transfers between accounts

**Files:**
- Create: `model/finance/rest/TransferRequest.java`, `TransferDTO.java`, `service/TransferService.java`, `controller/TransferController.java`
- Test: `controller/TransferControllerTest.java`

**Interfaces:**
- Consumes: `AccountService.require(UUID)`, `TransactionRepository.findAllByTransferGroupId(UUID)`, `NotFoundException`.
- Produces:
  - `TransferRequest(UUID fromAccountId, UUID toAccountId, BigDecimal amount, LocalDate occurredOn, String description)` — `amount` `@Positive`
  - `TransferDTO(UUID transferGroupId, UUID fromTransactionId, UUID toTransactionId)`
  - `TransferService.create(TransferRequest): TransferDTO`, `delete(UUID groupId): void`
  - `POST /api/transfers`, `DELETE /api/transfers/{groupId}`

- [ ] **Step 1: Write the failing `TransferControllerTest`** — cases: both legs written and both balances move (assert via `sumByAccountId` and `GET /api/accounts`); both legs share one group id, are `TRANSFER`, and carry no category; same-account transfer → 400 `A transfer needs two different accounts`; zero or negative amount → 400 with `errors.amount`; unknown account → 404; deleting the transfer removes both legs and restores both balances; unknown group → 404 `Transfer not found`.
- [ ] **Step 2: Run it** — Expected: FAIL, no controller.
- [ ] **Step 3: Write the two DTOs.**
- [ ] **Step 4: Write `TransferService`** — one `@Transactional` `create` generating the group id and saving the negative source leg then the positive destination leg; `delete` loads both legs by group id, 404s when empty, deletes them together. Javadoc why both rows are written in one database transaction.
- [ ] **Step 5: Write `TransferController`.**
- [ ] **Step 6: Run the test** — Expected: PASS, 7 tests.
- [ ] **Step 7: Commit** — `feat: add transfers as two linked transaction legs`.

---

## Task 8: Reports — summary, monthly series, category totals

**Files:**
- Create: `dao/ReportRepository.java`, `dao/MonthlyTotalRow.java`, `dao/CategoryTotalRow.java`
- Create: `model/finance/rest/SummaryDTO.java`, `MonthlyPointDTO.java`, `CategoryTotalDTO.java`
- Create: `service/ReportService.java`, `controller/ReportController.java`
- Test: `controller/ReportControllerTest.java`

**Interfaces:**
- Consumes: `AccountService.totalBalance()`, the transaction table.
- Produces:
  - `ReportRepository`: `incomeBetween(LocalDate, LocalDate): BigDecimal`, `expensesBetween(LocalDate, LocalDate): BigDecimal` (positive magnitude), `monthlyTotals(LocalDate, LocalDate): List<MonthlyTotalRow>`, `categoryTotals(LocalDate, LocalDate, String kind): List<CategoryTotalRow>`
  - `MonthlyTotalRow{getMonth(): String, getIncome(), getExpenses()}`, `CategoryTotalRow{getCategoryId(), getCategoryName(), getTotal()}`
  - `SummaryDTO(String month, BigDecimal totalBalance, BigDecimal income, BigDecimal expenses, BigDecimal savings)`
  - `MonthlyPointDTO(String month, BigDecimal income, BigDecimal expenses)` — `month` is `"YYYY-MM"`
  - `CategoryTotalDTO(UUID categoryId, String categoryName, BigDecimal total)`
  - `ReportService.summary(YearMonth)`, `monthly(LocalDate, LocalDate)`, `byCategory(LocalDate, LocalDate, CategoryKind)`
  - `GET /api/reports/summary?month=YYYY-MM`, `/api/reports/monthly?from&to`, `/api/reports/by-category?from&to&kind`

- [ ] **Step 1: Write the failing `ReportControllerTest`** — seed two months of activity plus one transfer, then assert: summary income/expenses/savings for the month; `totalBalance` covers every account all time and ignores `month`; a month with no activity is all zeros; `month=September` → 400 `month must be in YYYY-MM format`; the monthly series emits zero-filled months for gaps; category totals sort by total descending; category totals also work for `kind=INCOME`. The seeded transfer is what proves the `transfer_group_id IS NULL` filters work.
- [ ] **Step 2: Run it** — Expected: FAIL, no controller.
- [ ] **Step 3: Write the two projections.**
- [ ] **Step 4: Write `ReportRepository`** — native Postgres queries; `to_char(occurred_on,'YYYY-MM')` for bucketing, `ABS(SUM(...))` for magnitudes, `FILTER (WHERE type = ...)` to get income and expenses in one grouped pass. Javadoc why they are native (JPQL cannot express these portably) and that every query excludes transfer legs.
- [ ] **Step 5: Write the three report DTOs** — javadoc `expenses`/`total` as positive magnitudes and `savings` as possibly negative.
- [ ] **Step 6: Write `ReportService`** — `summary` derives the month's date range and `savings = income − expenses`; `monthly` walks `YearMonth` from `from` to `to` filling absent months with zeros so charts have no gaps — comment that reason.
- [ ] **Step 7: Write `ReportController`** — `YearMonth` has no Spring converter, so parse it in the controller and rethrow a `DateTimeParseException` as `IllegalArgumentException("month must be in YYYY-MM format")`.
- [ ] **Step 8: Run the test** — Expected: PASS, 7 tests. Income or expenses inflated by the transfer amount means a query lost its `transfer_group_id IS NULL` filter.
- [ ] **Step 9: Commit** — `feat: add summary, monthly and by-category reports`.

---

## Task 9: CSV import with preview and duplicate skipping

**Files:**
- Create: `model/finance/rest/ImportRequest.java`, `ImportResultDTO.java`, `ImportPreviewDTO.java`
- Create: `service/CsvImportService.java`, `controller/CsvIoController.java`
- Test: `controller/CsvImportTest.java`

**Interfaces:**
- Consumes: `AccountService.require(UUID)`, `TransactionRepository.{save, existsByAccountIdAndOccurredOnAndAmountAndDescription}`.
- Produces:
  - `ImportRequest(UUID accountId, String dateColumn, String amountColumn, String descriptionColumn, String dateFormat)` — sent as multipart form fields alongside `file`
  - `ImportResultDTO(int imported, int skipped, List<RowError> errors)` with nested `RowError(int row, String message)`
  - `ImportPreviewDTO(List<String> headers, List<List<String>> sampleRows)` — first 5 data rows
  - `CsvImportService.preview(MultipartFile): ImportPreviewDTO`, `importTransactions(MultipartFile, ImportRequest): ImportResultDTO`
  - `POST /api/import/preview`, `POST /api/import/transactions` (both `multipart/form-data`)

- [ ] **Step 1: Write the failing `CsvImportTest`** — build CSVs as `MockMultipartFile` bodies. Cases: preview returns the header list and at most 5 sample rows; a positive amount imports as INCOME and a negative as EXPENSE, both with a null category; re-importing the same file imports 0 and skips every row; a row with an unparseable date is reported in `errors` with its 1-based row number while the valid rows still commit; a zero-amount row is an error, not a skip; a custom `dateFormat` such as `MM/dd/yyyy` parses; an unknown column name → 400 naming the missing column; an unknown `accountId` → 404.
- [ ] **Step 2: Run it** — Expected: FAIL, no controller.
- [ ] **Step 3: Write the three DTOs** — javadoc the skip-versus-error distinction: a duplicate is a skip, a parse failure is an error.
- [ ] **Step 4: Write `CsvImportService`** — Apache Commons CSV with header parsing and trimming; per row, parse the date with the supplied pattern, parse the amount, derive the type from its sign, and look for an existing row matching account, date, amount and description. Collect errors per row rather than aborting, and comment why: one bad line should not cost the user the rest of the file.
- [ ] **Step 5: Write `CsvIoController`** — `@RequestPart` file plus `@ModelAttribute` request fields; return 200 with the result.
- [ ] **Step 6: Run the test** — Expected: PASS, 8 tests.
- [ ] **Step 7: Commit** — `feat: add CSV import with column mapping and duplicate skipping`.

---

## Task 10: CSV export and the OpenAPI check

**Files:**
- Modify: `controller/CsvIoController.java` (add the export route)
- Create: `service/CsvExportService.java`
- Test: `controller/CsvExportTest.java`

**Interfaces:**
- Consumes: `TransactionRepository` via `TransactionSpecifications.filter`.
- Produces: `CsvExportService.export(LocalDate from, LocalDate to): String`; `GET /api/export/transactions.csv?from&to` returning `text/csv` with `Content-Disposition: attachment; filename="transactions-<from>-<to>.csv"`.

- [ ] **Step 1: Write the failing `CsvExportTest`** — cases: the header row is exactly `date,account,category,type,amount,description`; one row per transaction in the range, ordered oldest first; amounts are signed as stored so the file reconciles against balances; transfers are included with an empty category; a description containing a comma is quoted; an empty range returns just the header; the `Content-Disposition` filename carries both dates.
- [ ] **Step 2: Run it** — Expected: FAIL, no export route.
- [ ] **Step 3: Write `CsvExportService`** — Commons CSV printer over the filtered, date-ordered transactions.
- [ ] **Step 4: Add the export endpoint to `CsvIoController`** — produce `text/csv`, set the attachment header.
- [ ] **Step 5: Run the test** — Expected: PASS, 7 tests.
- [ ] **Step 6: Run the whole API suite** — `./gradlew test`. Expected: green, every task's tests together.
- [ ] **Step 7: Check the generated API docs** — `./gradlew bootRun`, open `http://localhost:8080/swagger-ui.html`, confirm all six controllers appear. This is the contract the UI tasks code against.
- [ ] **Step 8: Commit** — `feat: add CSV export of transactions over a date range`.

---

## Task 11: UI scaffolding, API client, and formatting

**Files (all in `expence-ui/`):**
- Create: Next.js app via `create-next-app` (TypeScript, App Router, Tailwind) — replaces the abandoned CRA scaffold
- Create: `.env.local`, `lib/types.ts`, `lib/api/client.ts`, `lib/format.ts`, `app/layout.tsx`, `app/providers.tsx`
- Test: `vitest.config.ts`, `lib/format.test.ts`, `lib/api/client.test.ts`

**Interfaces:**
- Consumes: the API contract from Tasks 4–10.
- Produces:
  - `lib/types.ts` — `Account`, `AccountType`, `Category`, `CategoryKind`, `Transaction`, `TransactionType`, `Page<T>`, `Summary`, `MonthlyPoint`, `CategoryTotal`, `ImportResult`, `ImportPreview`, mirroring the API DTOs field for field
  - `ApiError extends Error` with `status: number`, `message: string`, `fieldErrors?: Record<string, string>`
  - `apiFetch<T>(path: string, init?: RequestInit): Promise<T>` — prefixes `NEXT_PUBLIC_API_URL`, sets JSON headers, throws `ApiError` on non-2xx, returns `undefined` for 204
  - `formatMoney(value: number): string`, `formatDate(iso: string): string`
  - `app/providers.tsx` exporting a client component wrapping children in `QueryClientProvider`

- [ ] **Step 1: Delete the CRA leftovers and scaffold Next.js** — the repo's tracked CRA files are already deleted in the working tree; scaffold in place, then install `@tanstack/react-query`, `recharts`, and dev deps `vitest`, `@testing-library/react`, `@testing-library/user-event`, `jsdom`, `msw`.
- [ ] **Step 2: Write `.env.local`** — `NEXT_PUBLIC_API_URL=http://localhost:8080`.
- [ ] **Step 3: Write the failing `lib/format.test.ts`** — cases: `0` → `$0.00`; `1234.5` → `$1,234.50`; `-42` renders in parentheses; rounds to 2 decimals; `formatDate('2026-09-04')` renders without timezone drift (the bug to guard: `new Date('2026-09-04')` is UTC midnight and prints as the 3rd west of Greenwich).
- [ ] **Step 4: Run `npx vitest run`** — Expected: FAIL, module not found.
- [ ] **Step 5: Write `lib/format.ts`** — `Intl.NumberFormat` for money; parse the ISO date as local parts, not via `new Date(iso)`.
- [ ] **Step 6: Write the failing `lib/api/client.test.ts`** — cases: a 2xx JSON body is returned typed; a 204 resolves to `undefined`; a 400 with `{message, errors}` throws an `ApiError` carrying `status`, `message` and `fieldErrors`; a 500 with a non-JSON body still throws an `ApiError` rather than a `SyntaxError`.
- [ ] **Step 7: Write `lib/api/client.ts` and `lib/types.ts`** — make the tests pass.
- [ ] **Step 8: Write `app/providers.tsx` and `app/layout.tsx`** — providers is `'use client'` and creates the `QueryClient` in `useState` so it is not shared across requests; layout renders the nav (Dashboard, Accounts, Transactions, Categories, Reports) and wraps children.
- [ ] **Step 9: Run `npx vitest run` and `npm run build`** — Expected: tests pass, build succeeds.
- [ ] **Step 10: Commit** — `feat: scaffold Next.js UI with typed API client and formatting`.

---

## Task 12: Typed API modules and query hooks

**Files:**
- Create: `lib/api/{accounts,categories,transactions,transfers,reports,csv}.ts`
- Create: `lib/hooks/{useAccounts,useCategories,useTransactions,useTransfers,useReports}.ts`
- Test: `lib/hooks/useTransactions.test.tsx`, `src/mocks/handlers.ts` (MSW)

**Interfaces:**
- Consumes: `apiFetch`, `lib/types.ts`.
- Produces:
  - One typed function per endpoint, e.g. `listAccounts(): Promise<Account[]>`, `createTransaction(body: TransactionInput): Promise<Transaction>`, `exportCsvUrl(from, to): string`
  - Query hooks `useAccounts()`, `useCategories(kind?)`, `useTransactions(filters)`, `useSummary(month)`, `useMonthly(from,to)`, `useByCategory(from,to,kind)`
  - Mutation hooks `useCreateAccount()`, `useUpdateAccount()`, `useDeleteAccount()`, the category and transaction equivalents, `useCreateTransfer()`, `useDeleteTransfer()`, `useImportCsv()`
  - Query-key convention: `['accounts']`, `['categories', kind]`, `['transactions', filters]`, `['reports', ...]`

- [ ] **Step 1: Write the MSW handlers** — stub each endpoint the hooks call, with fixture data.
- [ ] **Step 2: Write the failing `useTransactions.test.tsx`** — cases: the list hook returns mapped data; creating a transaction invalidates `['transactions']`, `['accounts']` and `['reports']` (assert by counting refetches against MSW); a mutation failure surfaces the `ApiError` message to the caller.
- [ ] **Step 3: Run `npx vitest run`** — Expected: FAIL, hooks do not exist.
- [ ] **Step 4: Write the API modules** — thin typed wrappers only; no logic beyond building query strings.
- [ ] **Step 5: Write the hooks** — every mutation invalidates the keys its write affects. Comment the rule once: a transaction changes balances and report totals, which is why the dashboard is correct the moment an edit lands.
- [ ] **Step 6: Run the test** — Expected: PASS.
- [ ] **Step 7: Commit** — `feat: add typed API modules and TanStack Query hooks`.

---

## Task 13: Shared UI components

**Files:**
- Create: `components/{MoneyText,DataTable,Dialog,ConfirmDialog,FormField,PageHeader,EmptyState,ErrorBanner}.tsx`
- Test: `components/MoneyText.test.tsx`, `components/Dialog.test.tsx`

**Interfaces:**
- Consumes: `formatMoney`.
- Produces:
  - `<MoneyText value={number} />` — formats via `formatMoney`, applies a negative/positive tone class
  - `<DataTable columns={Column<T>[]} rows={T[]} empty={ReactNode} />`
  - `<Dialog open onClose title>{children}</Dialog>` — focus trap, Escape to close
  - `<ConfirmDialog open title message confirmLabel onConfirm onCancel error?>` — renders a server 409 message inline
  - `<FormField label error?>{input}</FormField>` — wires the `fieldErrors` map from `ApiError` to the offending input
  - `<PageHeader title action?>`, `<EmptyState message action?>`, `<ErrorBanner error={ApiError|Error} />`

- [ ] **Step 1: Write the failing component tests** — `MoneyText`: negative renders in parentheses and carries the negative tone class. `Dialog`: Escape calls `onClose`, focus moves into the dialog on open, and the overlay click closes it.
- [ ] **Step 2: Run `npx vitest run`** — Expected: FAIL.
- [ ] **Step 3: Write the components** — Tailwind only, no component library. Keep them presentational: no data fetching inside.
- [ ] **Step 4: Run the tests** — Expected: PASS.
- [ ] **Step 5: Commit** — `feat: add shared UI components`.

---

## Task 14: Accounts and Categories pages

**Files:**
- Create: `app/accounts/page.tsx`, `app/accounts/AccountDialog.tsx`
- Create: `app/categories/page.tsx`
- Test: `app/accounts/page.test.tsx`, `app/categories/page.test.tsx`

**Interfaces:**
- Consumes: the account and category hooks from Task 12, the components from Task 13.
- Produces: two working pages; no new exported API.

- [ ] **Step 1: Write the failing page tests (MSW-backed)** — accounts: the table shows name, type, opening balance and current balance; the create dialog posts and the row appears; a 409 on delete renders the server message in the confirm dialog rather than a generic error; a 400 with `errors.name` renders under the name input. Categories: income and expense lists render separately; defaults are marked and their delete control is disabled; adding a category posts with the right `kind`; renaming posts only the name.
- [ ] **Step 2: Run `npx vitest run`** — Expected: FAIL.
- [ ] **Step 3: Build the accounts page and dialog.**
- [ ] **Step 4: Build the categories page.**
- [ ] **Step 5: Run the tests** — Expected: PASS.
- [ ] **Step 6: Verify against the running API** — start Postgres, `bootRun`, `npm run dev`; create, rename and delete an account by hand and confirm the 409 path.
- [ ] **Step 7: Commit** — `feat: add accounts and categories pages`.

---

## Task 15: Transactions page with transfer, import and export

**Files:**
- Create: `app/transactions/page.tsx`, `TransactionDrawer.tsx`, `TransferDialog.tsx`, `ImportDialog.tsx`, `FilterBar.tsx`
- Test: `app/transactions/page.test.tsx`, `app/transactions/ImportDialog.test.tsx`

**Interfaces:**
- Consumes: the transaction, transfer and CSV hooks from Task 12.
- Produces: the transactions page; no new exported API.

- [ ] **Step 1: Write the failing page tests** — cases: the table lists newest first with signed amounts; each filter control updates the query and refetches; the add drawer submits a positive magnitude plus a type (never a signed amount); a transfer-leg row's edit and delete controls are disabled with an explanation, matching the API rule; the transfer dialog rejects the same account on both sides before submitting; paging controls move between pages. Import: preview renders the file's headers and lets the user map columns; a completed import reports imported/skipped counts and lists row errors.
- [ ] **Step 2: Run `npx vitest run`** — Expected: FAIL.
- [ ] **Step 3: Build the filter bar and table.**
- [ ] **Step 4: Build the add/edit drawer and the transfer dialog.**
- [ ] **Step 5: Build the import dialog** — two steps, preview then map-and-import.
- [ ] **Step 6: Add the export button** — a link to `exportCsvUrl(from, to)` carrying the current date range.
- [ ] **Step 7: Run the tests** — Expected: PASS.
- [ ] **Step 8: Commit** — `feat: add transactions page with transfer, import and export`.

---

## Task 16: Dashboard and Reports pages

**Files:**
- Create: `app/page.tsx`, `components/charts/{CategoryDonut,IncomeExpenseBars}.tsx`
- Create: `app/reports/page.tsx`
- Test: `app/page.test.tsx`, `app/reports/page.test.tsx`

**Interfaces:**
- Consumes: `useSummary`, `useMonthly`, `useByCategory`, `useTransactions`, the chart components.
- Produces: the dashboard and reports pages.

- [ ] **Step 1: Write the failing page tests** — dashboard: four tiles render total balance, income, expenses and savings for the selected month; the month selector defaults to the current month and refetches on change; the donut renders one slice per category with an accessible label; the ten most recent transactions list. Reports: the date-range picker drives both the grouped bars and the category table; the table shows each category's share of the total; an empty range renders an empty state, not a broken chart.
- [ ] **Step 2: Run `npx vitest run`** — Expected: FAIL.
- [ ] **Step 3: Build the two chart components** — Recharts; before writing chart code, load the `dataviz` skill for palette and labelling rules.
- [ ] **Step 4: Build the dashboard.**
- [ ] **Step 5: Build the reports page.**
- [ ] **Step 6: Run the tests** — Expected: PASS.
- [ ] **Step 7: Commit** — `feat: add dashboard and reports pages`.

---

## Task 17: End-to-end tests against the real stack

**Files:**
- Create: `playwright.config.ts`, `e2e/{accounts-and-transactions,transfers,csv-import,csv-export}.spec.ts`, `e2e/fixtures/sample-transactions.csv`
- Modify: `expence-ui/package.json` (add `test:e2e`), `expence-api/README.md` and `expence-ui/README.md` (how to run everything)

**Interfaces:**
- Consumes: the whole stack — Docker Postgres, `./gradlew bootRun`, `next dev`.
- Produces: four Playwright specs and the run instructions.

- [ ] **Step 1: Write `playwright.config.ts`** — base URL `http://localhost:3000`, one browser, no retries locally.
- [ ] **Step 2: Write the four failing specs** — the four flows from the spec's *Testing* section: (1) create an account, add income and an expense, see the dashboard tiles and donut update; (2) transfer between two accounts, see both balances move while income and expenses do not; (3) import the fixture CSV, see the rows, re-import it and see every row skipped; (4) export and assert the downloaded file's header and row count.
- [ ] **Step 3: Run them with the stack up** — Expected: they pass if Tasks 1–16 are correct; each failure is a real defect, not a missing implementation.
- [ ] **Step 4: Write the two READMEs** — prerequisites, the three commands to start the stack, how to run each test suite.
- [ ] **Step 5: Run every suite once** — `./gradlew test`, `npx vitest run`, `npm run test:e2e`. Expected: all green.
- [ ] **Step 6: Commit** — `test: add Playwright end-to-end specs and run instructions`.

---

## Task 18: Phase 1 wrap-up

- [ ] **Step 1: Walk `phase1.md` line by line** — confirm every listed feature is reachable in the running app. Note anything missing as a defect, not as a new feature.
- [ ] **Step 2: Verify the backup/export deliverable** — export a CSV, confirm the amounts reconcile against the account balances shown in the UI.
- [ ] **Step 3: Confirm a clean start** — `docker compose down -v && docker compose up -d && ./gradlew bootRun` migrates an empty database and seeds the 12 default categories.
- [ ] **Step 4: Commit any fixes** and stop. Budgets, auth and automation are Phase 2.
