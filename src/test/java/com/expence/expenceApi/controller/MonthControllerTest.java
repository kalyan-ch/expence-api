package com.expence.expenceApi.controller;

import com.expence.expenceApi.AbstractIntegrationTest;
import com.expence.expenceApi.dao.AccountRepository;
import com.expence.expenceApi.dao.MonthBalanceRepository;
import com.expence.expenceApi.dao.PlanMonthRepository;
import com.expence.expenceApi.dao.RecurringItemRepository;
import com.expence.expenceApi.model.finance.Account;
import com.expence.expenceApi.model.finance.AccountType;
import com.expence.expenceApi.model.finance.ItemKind;
import com.expence.expenceApi.model.finance.MonthBalance;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Rolled back so plan rows never block other suites from deleting accounts.
// The numbers are the October 2026 sheet, so the sheet itself is the check.
@Transactional
@AutoConfigureMockMvc
class MonthControllerTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AccountRepository accountRepository;
    @Autowired RecurringItemRepository recurringItemRepository;
    @Autowired PlanMonthRepository planMonthRepository;
    @Autowired MonthBalanceRepository monthBalanceRepository;

    private Account chase;
    private Account dcu;
    private int order;

    @BeforeEach
    void seedSheet() {
        chase = account("Chase");
        dcu = account("DCU");
        bill("Rent", ItemKind.EXPENSE, chase, "2010.82", 1);
        bill("India", ItemKind.EXPENSE, chase, null, null);
        bill("Citi", ItemKind.SAVINGS, chase, null, null);
        bill("Amex Card", ItemKind.CARD_PAYMENT, dcu, "433.14", 27);
        bill("Sapphire Card", ItemKind.CARD_PAYMENT, chase, "159.15", 5);
        bill("Freedom Card", ItemKind.CARD_PAYMENT, chase, "255.29", 22);
        bill("Platinum Card", ItemKind.CARD_PAYMENT, dcu, "395.07", 16);
        bill("Car", ItemKind.EXPENSE, dcu, "379.81", 10);
        for (Account a : new Account[] {chase, dcu}) {
            bill("Salary 1 " + a.getName(), ItemKind.INCOME, a, "1985.72", null);
            bill("Salary 2 " + a.getName(), ItemKind.INCOME, a, "1985.72", null);
        }
    }

    private Account account(String name) {
        return accountRepository.save(Account.builder()
                .name(name + " " + UUID.randomUUID()).type(AccountType.CHECKING).openingBalance(BigDecimal.ZERO).paymentAccount(true).build());
    }

    private RecurringItem bill(String name, ItemKind kind, Account account, String amount, Integer day) {
        return recurringItemRepository.save(RecurringItem.builder().name(name).kind(kind).account(account)
                .defaultAmount(amount == null ? null : new BigDecimal(amount)).billingDay(day).sortOrder(order++).build());
    }

    private void balance(String month, Account account, String amount) {
        monthBalanceRepository.save(MonthBalance.builder()
                .planMonth(planMonthRepository.findByMonth(LocalDate.parse(month + "-01")).orElseThrow())
                .account(account).startingBalance(new BigDecimal(amount)).build());
    }

    @Test
    void octoberReproducesTheSheet() throws Exception {
        mockMvc.perform(get("/api/months/2026-10")).andExpect(status().isOk());
        balance("2026-10", chase, "2915.91");
        balance("2026-10", dcu, "4471.51");

        mockMvc.perform(get("/api/months/2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.items", hasSize(12)))
                .andExpect(jsonPath("$.total").value(3633.28))
                .andExpect(jsonPath("$.expenditure").value(3633.28))
                .andExpect(jsonPath("$.unpaidCount").value(6))
                .andExpect(jsonPath("$.accounts[0].accountName").value(chase.getName()))
                .andExpect(jsonPath("$.accounts[0].expenses").value(2425.26))
                .andExpect(jsonPath("$.accounts[0].income").value(3971.44))
                .andExpect(jsonPath("$.accounts[0].remaining").value(4462.09))
                .andExpect(jsonPath("$.accounts[1].expenses").value(1208.02))
                .andExpect(jsonPath("$.accounts[1].remaining").value(7234.93));
    }

    @Test
    void itemsSortByBillingDayWithBlankDaysLast() throws Exception {
        mockMvc.perform(get("/api/months/2026-10"))
                .andExpect(jsonPath("$.items[0].name").value("Rent"))
                .andExpect(jsonPath("$.items[1].name").value("Sapphire Card"))
                .andExpect(jsonPath("$.items[2].name").value("Car"))
                .andExpect(jsonPath("$.items[5].name").value("Amex Card"))
                .andExpect(jsonPath("$.items[6].name").value("India"));
    }

    @Test
    void savingsCountInTotalButNotExpenditure() throws Exception {
        bill("Marcus", ItemKind.SAVINGS, chase, "500", 3);
        mockMvc.perform(get("/api/months/2026-10"))
                .andExpect(jsonPath("$.total").value(4133.28))
                .andExpect(jsonPath("$.expenditure").value(3633.28))
                .andExpect(jsonPath("$.accounts[0].expenses").value(2925.26));
    }

    @Test
    void retiredItemsAreNotCopied() throws Exception {
        RecurringItem car = recurringItemRepository.findAll().stream()
                .filter(r -> r.getName().equals("Car")).findFirst().orElseThrow();
        car.setActive(false);
        mockMvc.perform(get("/api/months/2026-10"))
                .andExpect(jsonPath("$.items", hasSize(11)))
                .andExpect(jsonPath("$.expenditure").value(3253.47));
    }

    @Test
    void listReturnsExpenditureOldestFirst() throws Exception {
        mockMvc.perform(get("/api/months/2026-10"));
        mockMvc.perform(get("/api/months/2026-09"));
        mockMvc.perform(get("/api/months"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].month").value("2026-09"))
                .andExpect(jsonPath("$[1].expenditure").value(3633.28));
    }

    @Test
    void invalidMonthReturns400() throws Exception {
        mockMvc.perform(get("/api/months/2026-13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for 'month'"));
    }

    private String itemId(String name) throws Exception {
        String json = mockMvc.perform(get("/api/months/2026-10")).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("items").valueStream()
                .filter(i -> i.get("name").asString().equals(name)).findFirst().orElseThrow().get("id").asString();
    }

    private Map<String, Object> item(String name, ItemKind kind, Account account, String amount, Integer day) {
        var map = new HashMap<String, Object>();
        map.put("name", name);
        map.put("kind", kind.name());
        map.put("accountId", account.getId().toString());
        map.put("amount", amount);
        map.put("billingDay", day);
        return map;
    }

    private String json(Object o) {
        return objectMapper.writeValueAsString(o);
    }

    @Test
    void setBalanceThroughApiDrivesRemaining() throws Exception {
        mockMvc.perform(put("/api/months/2026-10/balances/" + chase.getId()).contentType("application/json")
                        .content("{\"startingBalance\": 2915.91}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts[0].startingBalance").value(2915.91))
                .andExpect(jsonPath("$.accounts[0].remaining").value(4462.09));
        mockMvc.perform(put("/api/months/2026-10/balances/" + chase.getId()).contentType("application/json")
                        .content("{\"startingBalance\": 3000}"))
                .andExpect(jsonPath("$.accounts[0].remaining").value(4546.18));
    }

    @Test
    void balanceForUnknownAccountReturns404() throws Exception {
        mockMvc.perform(put("/api/months/2026-10/balances/" + UUID.randomUUID()).contentType("application/json")
                        .content("{\"startingBalance\": 1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"));
    }

    @Test
    void editingAMonthChangesOnlyThatMonth() throws Exception {
        var body = item("Amex Card", ItemKind.CARD_PAYMENT, dcu, "500.00", 27);
        mockMvc.perform(put("/api/months/2026-10/items/" + itemId("Amex Card")).contentType("application/json").content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expenditure").value(3700.14));
        mockMvc.perform(get("/api/months/2026-11"))
                .andExpect(jsonPath("$.expenditure").value(3633.28));
    }

    @Test
    void markingPaidSetsTodayAndPendingClearsIt() throws Exception {
        String rent = itemId("Rent");
        var body = item("Rent", ItemKind.EXPENSE, chase, "2010.82", 1);
        body.put("status", "PAID");
        mockMvc.perform(put("/api/months/2026-10/items/" + rent).contentType("application/json").content(json(body)))
                .andExpect(jsonPath("$.items[0].status").value("PAID"))
                .andExpect(jsonPath("$.items[0].paidOn").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.unpaidCount").value(5));
        body.put("status", "PENDING");
        mockMvc.perform(put("/api/months/2026-10/items/" + rent).contentType("application/json").content(json(body)))
                .andExpect(jsonPath("$.items[0].paidOn").doesNotExist())
                .andExpect(jsonPath("$.unpaidCount").value(6));
    }

    @Test
    void oneOffItemCanBeAddedAndDeleted() throws Exception {
        mockMvc.perform(post("/api/months/2026-10/items").contentType("application/json")
                        .content(json(item("Car repair", ItemKind.EXPENSE, dcu, "120.00", 12))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items", hasSize(13)))
                .andExpect(jsonPath("$.items[3].name").value("Car repair"))
                .andExpect(jsonPath("$.items[3].recurringItemId").doesNotExist())
                .andExpect(jsonPath("$.expenditure").value(3753.28));
        mockMvc.perform(delete("/api/months/2026-10/items/" + itemId("Car repair")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(12)))
                .andExpect(jsonPath("$.expenditure").value(3633.28));
    }

    @Test
    void invalidItemReturns400() throws Exception {
        mockMvc.perform(post("/api/months/2026-10/items").contentType("application/json")
                        .content(json(item("", ItemKind.EXPENSE, dcu, "0", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.amount").exists())
                .andExpect(jsonPath("$.errors.billingDay").exists());
    }

    @Test
    void itemFromAnotherMonthReturns404() throws Exception {
        String rent = itemId("Rent");
        mockMvc.perform(delete("/api/months/2026-11/items/" + rent))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Bill not found"));
    }
}
