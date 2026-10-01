package com.expence.expenceApi.controller;

import com.expence.expenceApi.AbstractIntegrationTest;
import com.expence.expenceApi.dao.AccountRepository;
import com.expence.expenceApi.dao.CategoryRepository;
import com.expence.expenceApi.dao.TransactionRepository;
import com.expence.expenceApi.model.finance.Account;
import com.expence.expenceApi.model.finance.AccountType;
import com.expence.expenceApi.model.finance.Category;
import com.expence.expenceApi.model.finance.CategoryKind;
import com.expence.expenceApi.model.finance.Transaction;
import com.expence.expenceApi.model.finance.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seeds March and May 2026 (April left empty) plus a 500 transfer in March. Every
 * income or expense figure below would be off by 500 if a query lost its
 * {@code transfer_group_id IS NULL} filter.
 */
@AutoConfigureMockMvc
class ReportControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AccountRepository accountRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired TransactionRepository transactionRepository;

    private Category groceries;
    private Category rent;
    private Category salary;

    @BeforeEach
    void seed() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        Account checking = accountRepository.save(Account.builder()
                .name("Checking").type(AccountType.CHECKING).openingBalance(new BigDecimal("1000")).build());
        Account savings = accountRepository.save(Account.builder()
                .name("Savings").type(AccountType.SAVINGS).openingBalance(BigDecimal.ZERO).build());
        groceries = category("Groceries", CategoryKind.EXPENSE);
        rent = category("Rent", CategoryKind.EXPENSE);
        salary = category("Salary", CategoryKind.INCOME);

        save(checking, salary, "3000", TransactionType.INCOME, LocalDate.of(2026, 3, 1));
        save(checking, groceries, "-200", TransactionType.EXPENSE, LocalDate.of(2026, 3, 5));
        save(checking, groceries, "-100", TransactionType.EXPENSE, LocalDate.of(2026, 3, 20));
        save(checking, rent, "-1500", TransactionType.EXPENSE, LocalDate.of(2026, 3, 31));
        save(checking, salary, "2000", TransactionType.INCOME, LocalDate.of(2026, 5, 1));
        save(checking, groceries, "-50", TransactionType.EXPENSE, LocalDate.of(2026, 5, 10));

        UUID group = UUID.randomUUID();
        transactionRepository.save(Transaction.builder()
                .account(checking).amount(new BigDecimal("-500")).type(TransactionType.TRANSFER)
                .occurredOn(LocalDate.of(2026, 3, 15)).transferGroupId(group).build());
        transactionRepository.save(Transaction.builder()
                .account(savings).amount(new BigDecimal("500")).type(TransactionType.TRANSFER)
                .occurredOn(LocalDate.of(2026, 3, 15)).transferGroupId(group).build());
    }

    private Category category(String name, CategoryKind kind) {
        return categoryRepository.save(Category.builder()
                .name(name + "-" + UUID.randomUUID()).kind(kind).isDefault(false).build());
    }

    private void save(Account account, Category category, String amount,
                      TransactionType type, LocalDate occurredOn) {
        transactionRepository.save(Transaction.builder()
                .account(account).category(category)
                .amount(new BigDecimal(amount)).type(type)
                .occurredOn(occurredOn).build());
    }

    @Test
    void summaryReportsMonthIncomeExpensesAndSavings() throws Exception {
        mockMvc.perform(get("/api/reports/summary").param("month", "2026-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-03"))
                .andExpect(jsonPath("$.income").value(3000))
                .andExpect(jsonPath("$.expenses").value(1800))
                .andExpect(jsonPath("$.savings").value(1200));
    }

    @Test
    void totalBalanceCoversEveryAccountAllTimeAndIgnoresMonth() throws Exception {
        // 1000 opening + 1200 (March) + 1950 (May); the transfer nets to zero across accounts.
        mockMvc.perform(get("/api/reports/summary").param("month", "2026-03"))
                .andExpect(jsonPath("$.totalBalance").value(4150));
        mockMvc.perform(get("/api/reports/summary").param("month", "2020-01"))
                .andExpect(jsonPath("$.totalBalance").value(4150));
    }

    @Test
    void monthWithNoActivityIsAllZeros() throws Exception {
        mockMvc.perform(get("/api/reports/summary").param("month", "2026-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.income").value(0))
                .andExpect(jsonPath("$.expenses").value(0))
                .andExpect(jsonPath("$.savings").value(0));
    }

    @Test
    void malformedMonthReturns400() throws Exception {
        mockMvc.perform(get("/api/reports/summary").param("month", "September"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("month must be in YYYY-MM format"));
    }

    @Test
    void monthlySeriesZeroFillsGaps() throws Exception {
        mockMvc.perform(get("/api/reports/monthly").param("from", "2026-03-01").param("to", "2026-05-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].month").value("2026-03"))
                .andExpect(jsonPath("$[0].income").value(3000))
                .andExpect(jsonPath("$[0].expenses").value(1800))
                .andExpect(jsonPath("$[1].month").value("2026-04"))
                .andExpect(jsonPath("$[1].income").value(0))
                .andExpect(jsonPath("$[1].expenses").value(0))
                .andExpect(jsonPath("$[2].month").value("2026-05"))
                .andExpect(jsonPath("$[2].income").value(2000))
                .andExpect(jsonPath("$[2].expenses").value(50));
    }

    @Test
    void expenseCategoryTotalsSortByTotalDescending() throws Exception {
        mockMvc.perform(get("/api/reports/by-category")
                        .param("from", "2026-03-01").param("to", "2026-05-31").param("kind", "EXPENSE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].categoryId").value(rent.getId().toString()))
                .andExpect(jsonPath("$[0].categoryName").value(rent.getName()))
                .andExpect(jsonPath("$[0].total").value(1500))
                .andExpect(jsonPath("$[1].categoryName").value(groceries.getName()))
                .andExpect(jsonPath("$[1].total").value(350));
    }

    @Test
    void incomeCategoryTotals() throws Exception {
        mockMvc.perform(get("/api/reports/by-category")
                        .param("from", "2026-03-01").param("to", "2026-05-31").param("kind", "INCOME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].categoryName").value(salary.getName()))
                .andExpect(jsonPath("$[0].total").value(5000));
    }
}
