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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Rolled back so wiping the Flyway seed below never leaks into other suites.
@Transactional
@AutoConfigureMockMvc
class CategoryControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AccountRepository accountRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired TransactionRepository transactionRepository;

    private Category groceries;
    private Category salary;

    @BeforeEach
    void seed() {
        // Replace the Flyway seed with a known two-category set so counts are exact.
        // Bulk deletes run immediately; deleteAll() would queue behind the inserts below and hit UNIQUE.
        transactionRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        groceries = categoryRepository.save(Category.builder()
                .name("Groceries").kind(CategoryKind.EXPENSE).isDefault(true).build());
        salary = categoryRepository.save(Category.builder()
                .name("Salary").kind(CategoryKind.INCOME).isDefault(false).build());
    }

    @Test
    void listsAllCategories() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Groceries", "Salary")));
    }

    @Test
    void filtersByKind() throws Exception {
        mockMvc.perform(get("/api/categories").param("kind", "INCOME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Salary"));
    }

    @Test
    void createsCustomCategoryNotMarkedDefault() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Pets", "kind", "EXPENSE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Pets"))
                .andExpect(jsonPath("$.kind").value("EXPENSE"))
                .andExpect(jsonPath("$.isDefault").value(false));
    }

    @Test
    void duplicateNameWithinKindReturns409() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "groceries", "kind", "EXPENSE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An expense category named 'groceries' already exists"));
    }

    @Test
    void sameNameInOtherKindIsAllowed() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Groceries", "kind", "INCOME"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("INCOME"));
    }

    @Test
    void renameDoesNotChangeKind() throws Exception {
        mockMvc.perform(put("/api/categories/" + groceries.getId())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Food", "kind", "INCOME"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Food"))
                .andExpect(jsonPath("$.kind").value("EXPENSE"));
    }

    @Test
    void deleteUnusedCustomCategoryReturns204() throws Exception {
        mockMvc.perform(delete("/api/categories/" + salary.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteDefaultCategoryReturns409() throws Exception {
        mockMvc.perform(delete("/api/categories/" + groceries.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("'Groceries' is a default category and cannot be deleted"));
    }

    @Test
    void deleteCategoryInUseReturns409() throws Exception {
        Category pets = categoryRepository.save(Category.builder()
                .name("Pets").kind(CategoryKind.EXPENSE).isDefault(false).build());
        Account account = accountRepository.save(Account.builder()
                .name("Checking").type(AccountType.CHECKING).openingBalance(BigDecimal.ZERO).build());
        transactionRepository.save(Transaction.builder()
                .account(account).category(pets)
                .amount(new BigDecimal("-20")).type(TransactionType.EXPENSE)
                .occurredOn(LocalDate.now()).build());

        mockMvc.perform(delete("/api/categories/" + pets.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Category 'Pets' is used by 1 transaction and cannot be deleted"));
    }

    @Test
    void unknownIdReturns404() throws Exception {
        mockMvc.perform(delete("/api/categories/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Category not found"));
    }
}
