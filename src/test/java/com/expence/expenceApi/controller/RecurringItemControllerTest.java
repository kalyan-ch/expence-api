package com.expence.expenceApi.controller;

import com.expence.expenceApi.AbstractIntegrationTest;
import com.expence.expenceApi.dao.AccountRepository;
import com.expence.expenceApi.dao.MonthItemRepository;
import com.expence.expenceApi.dao.PlanMonthRepository;
import com.expence.expenceApi.dao.RecurringItemRepository;
import com.expence.expenceApi.model.finance.Account;
import com.expence.expenceApi.model.finance.AccountType;
import com.expence.expenceApi.model.finance.ItemKind;
import com.expence.expenceApi.model.finance.MonthItem;
import com.expence.expenceApi.model.finance.PlanMonth;
import com.expence.expenceApi.model.finance.RecurringItem;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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

// Rolled back so recurring items never block other suites from deleting accounts.
@Transactional
@AutoConfigureMockMvc
class RecurringItemControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AccountRepository accountRepository;
    @Autowired RecurringItemRepository recurringItemRepository;
    @Autowired PlanMonthRepository planMonthRepository;
    @Autowired MonthItemRepository monthItemRepository;

    private Account chase;

    @BeforeEach
    void seed() {
        chase = accountRepository.save(Account.builder()
                .name("Chase " + UUID.randomUUID()).type(AccountType.CHECKING).openingBalance(BigDecimal.ZERO).paymentAccount(true).build());
    }

    private Map<String, Object> body(String name, ItemKind kind, String amount, Integer billingDay) {
        var map = new HashMap<String, Object>();
        map.put("name", name);
        map.put("kind", kind.name());
        map.put("accountId", chase.getId().toString());
        map.put("defaultAmount", amount);
        map.put("billingDay", billingDay);
        return map;
    }

    private RecurringItem save(String name, int sortOrder) {
        return recurringItemRepository.save(RecurringItem.builder()
                .name(name).kind(ItemKind.EXPENSE).account(chase).sortOrder(sortOrder).build());
    }

    private String json(Object o) {
        return objectMapper.writeValueAsString(o);
    }

    @Test
    void createReturns201ActiveWithAccountName() throws Exception {
        mockMvc.perform(post("/api/recurring-items").contentType("application/json")
                        .content(json(body("Rent", ItemKind.EXPENSE, "2010.82", 1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Rent"))
                .andExpect(jsonPath("$.kind").value("EXPENSE"))
                .andExpect(jsonPath("$.accountName").value(chase.getName()))
                .andExpect(jsonPath("$.defaultAmount").value(2010.82))
                .andExpect(jsonPath("$.billingDay").value(1))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void blankAmountAndDayAreAllowed() throws Exception {
        mockMvc.perform(post("/api/recurring-items").contentType("application/json")
                        .content(json(body("India", ItemKind.EXPENSE, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.defaultAmount").doesNotExist())
                .andExpect(jsonPath("$.billingDay").doesNotExist());
    }

    @Test
    void invalidAmountAndDayReturn400() throws Exception {
        mockMvc.perform(post("/api/recurring-items").contentType("application/json")
                        .content(json(body("Car", ItemKind.EXPENSE, "-5", 32))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.defaultAmount").exists())
                .andExpect(jsonPath("$.errors.billingDay").exists());
    }

    @Test
    void unknownAccountReturns404() throws Exception {
        var body = body("Car", ItemKind.EXPENSE, "379.81", 10);
        body.put("accountId", UUID.randomUUID().toString());
        mockMvc.perform(post("/api/recurring-items").contentType("application/json").content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"));
    }

    @Test
    void nonPaymentAccountReturns400() throws Exception {
        Account visa = accountRepository.save(Account.builder()
                .name("Visa " + UUID.randomUUID()).type(AccountType.CREDIT_CARD).openingBalance(BigDecimal.ZERO).build());
        var body = body("Car", ItemKind.EXPENSE, "379.81", 10);
        body.put("accountId", visa.getId().toString());
        mockMvc.perform(post("/api/recurring-items").contentType("application/json").content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'" + visa.getName() + "' is not a payment account"));
    }

    @Test
    void keepsItsAccountAfterItStopsBeingAPaymentAccount() throws Exception {
        RecurringItem car = save("Car", 0);
        chase.setPaymentAccount(false);
        mockMvc.perform(put("/api/recurring-items/" + car.getId()).contentType("application/json")
                        .content(json(body("Car", ItemKind.EXPENSE, "400.00", 10))))
                .andExpect(status().isOk());
    }

    @Test
    void duplicateNameIgnoringCaseReturns409() throws Exception {
        save("Rent", 0);
        mockMvc.perform(post("/api/recurring-items").contentType("application/json")
                        .content(json(body("rent", ItemKind.EXPENSE, "1", 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A bill named 'rent' already exists"));
    }

    @Test
    void listsInSheetOrder() throws Exception {
        save("Car", 11);
        save("Rent", 1);
        save("Amex Card", 6);
        mockMvc.perform(get("/api/recurring-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Rent"))
                .andExpect(jsonPath("$[1].name").value("Amex Card"))
                .andExpect(jsonPath("$[2].name").value("Car"));
    }

    @Test
    void updateCanRetireAndChangeAmount() throws Exception {
        RecurringItem car = save("Car", 0);
        var body = body("Car", ItemKind.EXPENSE, "400.00", 10);
        body.put("active", false);
        mockMvc.perform(put("/api/recurring-items/" + car.getId()).contentType("application/json").content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultAmount").value(400))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deleteUnusedReturns204() throws Exception {
        RecurringItem misc = save("Misc", 0);
        mockMvc.perform(delete("/api/recurring-items/" + misc.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteUsedByAMonthReturns409() throws Exception {
        RecurringItem rent = save("Rent", 0);
        PlanMonth october = planMonthRepository.save(PlanMonth.builder().month(LocalDate.of(2026, 10, 1)).build());
        monthItemRepository.save(MonthItem.builder().planMonth(october).recurringItem(rent)
                .name("Rent").kind(ItemKind.EXPENSE).account(chase).build());

        mockMvc.perform(delete("/api/recurring-items/" + rent.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("'Rent' is used by 1 month; retire it instead"));
    }

    @Test
    void unknownIdReturns404() throws Exception {
        mockMvc.perform(delete("/api/recurring-items/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Bill not found"));
    }
}
