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
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TransactionControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AccountRepository accountRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired TransactionRepository transactionRepository;

    private Account checking;
    private Account savings;
    private Category groceries;
    private Category salary;

    @BeforeEach
    void seed() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        checking = accountRepository.save(Account.builder()
                .name("Checking").type(AccountType.CHECKING).openingBalance(BigDecimal.ZERO).build());
        savings = accountRepository.save(Account.builder()
                .name("Savings").type(AccountType.SAVINGS).openingBalance(BigDecimal.ZERO).build());
        groceries = categoryRepository.save(Category.builder()
                .name("Groceries-" + UUID.randomUUID()).kind(CategoryKind.EXPENSE).isDefault(false).build());
        salary = categoryRepository.save(Category.builder()
                .name("Salary-" + UUID.randomUUID()).kind(CategoryKind.INCOME).isDefault(false).build());
    }

    private Map<String, Object> body(Account account, Category category, String amount,
                                     TransactionType type, LocalDate occurredOn) {
        var map = new HashMap<String, Object>();
        map.put("accountId", account.getId().toString());
        map.put("categoryId", category == null ? null : category.getId().toString());
        map.put("amount", amount);
        map.put("type", type.name());
        map.put("occurredOn", occurredOn.toString());
        map.put("description", "test");
        return map;
    }

    private Transaction save(Account account, Category category, String amount,
                             TransactionType type, LocalDate occurredOn) {
        return transactionRepository.save(Transaction.builder()
                .account(account).category(category)
                .amount(new BigDecimal(amount)).type(type)
                .occurredOn(occurredOn).build());
    }

    @Test
    void expenseIsStoredNegativeWithAccountAndCategoryNames() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                body(checking, groceries, "25.00", TransactionType.EXPENSE, LocalDate.now()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(-25.00))
                .andExpect(jsonPath("$.accountName").value("Checking"))
                .andExpect(jsonPath("$.categoryName").value(groceries.getName()));
    }

    @Test
    void incomeStaysPositive() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                body(checking, salary, "1000.00", TransactionType.INCOME, LocalDate.now()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(1000.00));
    }

    @Test
    void negativeMagnitudeReturns400() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                body(checking, groceries, "-25.00", TransactionType.EXPENSE, LocalDate.now()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());
    }

    @Test
    void incomeWithExpenseCategoryReturns409() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                body(checking, groceries, "10.00", TransactionType.INCOME, LocalDate.now()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Category '" + groceries.getName()
                        + "' is an expense category and cannot be used on an income transaction"));
    }

    @Test
    void transferTypeReturns400() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                body(checking, null, "10.00", TransactionType.TRANSFER, LocalDate.now()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Use POST /api/transfers to move money between accounts"));
    }

    @Test
    void unknownAccountReturns404() throws Exception {
        var request = body(checking, null, "10.00", TransactionType.EXPENSE, LocalDate.now());
        request.put("accountId", UUID.randomUUID().toString());

        mockMvc.perform(post("/api/transactions")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"));
    }

    @Test
    void listIsNewestFirstAndPaged() throws Exception {
        save(checking, groceries, "-10", TransactionType.EXPENSE, LocalDate.of(2026, 1, 1));
        save(checking, groceries, "-20", TransactionType.EXPENSE, LocalDate.of(2026, 3, 1));
        save(checking, groceries, "-30", TransactionType.EXPENSE, LocalDate.of(2026, 2, 1));

        mockMvc.perform(get("/api/transactions").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].occurredOn").value("2026-03-01"))
                .andExpect(jsonPath("$.content[1].occurredOn").value("2026-02-01"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void eachFilterNarrowsTheResult() throws Exception {
        save(checking, groceries, "-10", TransactionType.EXPENSE, LocalDate.of(2026, 1, 15));
        save(savings, salary, "500", TransactionType.INCOME, LocalDate.of(2026, 5, 15));

        mockMvc.perform(get("/api/transactions").param("from", "2026-05-01"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("INCOME"));

        mockMvc.perform(get("/api/transactions").param("to", "2026-01-31"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("EXPENSE"));

        mockMvc.perform(get("/api/transactions").param("accountId", savings.getId().toString()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].accountName").value("Savings"));

        mockMvc.perform(get("/api/transactions").param("categoryId", groceries.getId().toString()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].categoryName").value(groceries.getName()));

        mockMvc.perform(get("/api/transactions").param("type", "INCOME"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(500));
    }

    @Test
    void updateReappliesSignAndMovesAccount() throws Exception {
        Transaction transaction = save(checking, groceries, "-10", TransactionType.EXPENSE, LocalDate.now());

        mockMvc.perform(put("/api/transactions/" + transaction.getId())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                body(savings, groceries, "42.50", TransactionType.EXPENSE, LocalDate.now()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(-42.50))
                .andExpect(jsonPath("$.accountName").value("Savings"));
    }

    @Test
    void deleteReturns204() throws Exception {
        Transaction transaction = save(checking, groceries, "-10", TransactionType.EXPENSE, LocalDate.now());

        mockMvc.perform(delete("/api/transactions/" + transaction.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void editingOrDeletingTransferLegReturns409() throws Exception {
        UUID group = UUID.randomUUID();
        Transaction leg = transactionRepository.save(Transaction.builder()
                .account(checking).amount(new BigDecimal("-100")).type(TransactionType.TRANSFER)
                .occurredOn(LocalDate.now()).transferGroupId(group).build());
        transactionRepository.save(Transaction.builder()
                .account(savings).amount(new BigDecimal("100")).type(TransactionType.TRANSFER)
                .occurredOn(LocalDate.now()).transferGroupId(group).build());

        String message = "This transaction is part of a transfer; edit or delete the transfer instead";

        mockMvc.perform(put("/api/transactions/" + leg.getId())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                body(checking, groceries, "5.00", TransactionType.EXPENSE, LocalDate.now()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(message));

        mockMvc.perform(delete("/api/transactions/" + leg.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(message));
    }
}
