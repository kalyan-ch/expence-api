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
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AccountControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AccountRepository accountRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired CategoryRepository categoryRepository;

    private Category expenseCategory;

    @BeforeEach
    void clean() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        // Seed one category for transaction tests; don't rely on Flyway count.
        expenseCategory = categoryRepository.save(Category.builder()
                .name("Groceries-" + UUID.randomUUID())
                .kind(CategoryKind.EXPENSE)
                .isDefault(false)
                .build());
    }

    @Test
    void createReturns201WithBalanceEqualToOpeningBalance() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Checking", "type", "CHECKING", "openingBalance", 100))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Checking"))
                .andExpect(jsonPath("$.openingBalance").value(100))
                .andExpect(jsonPath("$.balance").value(100));
    }

    @Test
    void balanceIsOpeningPlusTransactionSum() throws Exception {
        Account account = accountRepository.save(Account.builder()
                .name("Checking").type(AccountType.CHECKING).openingBalance(new BigDecimal("100")).build());
        transactionRepository.save(Transaction.builder()
                .account(account).category(expenseCategory)
                .amount(new BigDecimal("-30")).type(TransactionType.EXPENSE)
                .occurredOn(LocalDate.now()).build());
        // Income positive 50
        Category incomeCat = categoryRepository.save(Category.builder()
                .name("Salary-" + UUID.randomUUID()).kind(CategoryKind.INCOME).isDefault(false).build());
        transactionRepository.save(Transaction.builder()
                .account(account).category(incomeCat)
                .amount(new BigDecimal("50")).type(TransactionType.INCOME)
                .occurredOn(LocalDate.now()).build());

        mockMvc.perform(get("/api/accounts/" + account.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(120));
    }

    @Test
    void paymentAccountFlagDefaultsOffAndCanBeSet() throws Exception {
        mockMvc.perform(post("/api/accounts").contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Visa", "type", "CREDIT_CARD", "openingBalance", 0))))
                .andExpect(jsonPath("$.paymentAccount").value(false));
        mockMvc.perform(post("/api/accounts").contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Chase", "type", "CHECKING", "openingBalance", 0, "paymentAccount", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentAccount").value(true));
    }

    @Test
    void accountWithNoActivityListsAtOpeningBalance() throws Exception {
        Account account = accountRepository.save(Account.builder()
                .name("Savings").type(AccountType.SAVINGS).openingBalance(new BigDecimal("250")).build());

        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].balance").value(250));
    }

    @Test
    void blankNameReturns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", " ", "type", "CHECKING", "openingBalance", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void duplicateNameCaseInsensitivelyReturns409() throws Exception {
        accountRepository.save(Account.builder().name("Checking").type(AccountType.CHECKING).openingBalance(BigDecimal.ZERO).build());

        mockMvc.perform(post("/api/accounts")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "checking", "type", "CASH", "openingBalance", 0))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account named 'checking' already exists"));
    }

    @Test
    void updateChangesNameTypeAndOpeningBalance() throws Exception {
        Account account = accountRepository.save(Account.builder()
                .name("Old").type(AccountType.CHECKING).openingBalance(BigDecimal.ZERO).build());

        mockMvc.perform(put("/api/accounts/" + account.getId())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "New", "type", "SAVINGS", "openingBalance", 500))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"))
                .andExpect(jsonPath("$.type").value("SAVINGS"))
                .andExpect(jsonPath("$.openingBalance").value(500));
    }

    @Test
    void keepingOwnNameOnUpdateIsNotDuplicate() throws Exception {
        Account account = accountRepository.save(Account.builder()
                .name("Checking").type(AccountType.CHECKING).openingBalance(BigDecimal.ZERO).build());

        mockMvc.perform(put("/api/accounts/" + account.getId())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Checking", "type", "CHECKING", "openingBalance", 0))))
                .andExpect(status().isOk());
    }

    @Test
    void deleteEmptyAccountReturns204() throws Exception {
        Account account = accountRepository.save(Account.builder()
                .name("Empty").type(AccountType.CASH).openingBalance(BigDecimal.ZERO).build());

        mockMvc.perform(delete("/api/accounts/" + account.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteWithTransactionsReturns409Singular() throws Exception {
        Account account = accountRepository.save(Account.builder()
                .name("Busy").type(AccountType.CHECKING).openingBalance(BigDecimal.ZERO).build());
        transactionRepository.save(Transaction.builder()
                .account(account).category(expenseCategory)
                .amount(new BigDecimal("-10")).type(TransactionType.EXPENSE)
                .occurredOn(LocalDate.now()).build());

        mockMvc.perform(delete("/api/accounts/" + account.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Account 'Busy' has 1 transaction and cannot be deleted"));
    }

    @Test
    void deleteWithTwoTransactionsPluralises() throws Exception {
        Account account = accountRepository.save(Account.builder()
                .name("Busy2").type(AccountType.CHECKING).openingBalance(BigDecimal.ZERO).build());
        transactionRepository.save(Transaction.builder()
                .account(account).category(expenseCategory)
                .amount(new BigDecimal("-10")).type(TransactionType.EXPENSE)
                .occurredOn(LocalDate.now()).build());
        transactionRepository.save(Transaction.builder()
                .account(account).category(expenseCategory)
                .amount(new BigDecimal("-5")).type(TransactionType.EXPENSE)
                .occurredOn(LocalDate.now()).build());

        mockMvc.perform(delete("/api/accounts/" + account.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Account 'Busy2' has 2 transactions and cannot be deleted"));
    }

    @Test
    void unknownIdReturns404() throws Exception {
        mockMvc.perform(get("/api/accounts/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"));
    }
}
