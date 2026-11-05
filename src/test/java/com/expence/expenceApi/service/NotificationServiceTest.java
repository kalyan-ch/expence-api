package com.expence.expenceApi.service;

import com.expence.expenceApi.AbstractIntegrationTest;
import com.expence.expenceApi.dao.AccountRepository;
import com.expence.expenceApi.dao.MonthBalanceRepository;
import com.expence.expenceApi.dao.MonthItemRepository;
import com.expence.expenceApi.dao.NotificationLogRepository;
import com.expence.expenceApi.dao.PlanMonthRepository;
import com.expence.expenceApi.model.finance.Account;
import com.expence.expenceApi.model.finance.AccountType;
import com.expence.expenceApi.model.finance.ItemKind;
import com.expence.expenceApi.model.finance.ItemStatus;
import com.expence.expenceApi.model.finance.MonthBalance;
import com.expence.expenceApi.model.finance.MonthItem;
import com.expence.expenceApi.model.finance.PlanMonth;
import com.expence.expenceApi.service.DiscordNotifier.Message;
import com.expence.expenceApi.service.DueBillService.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Rolled back, and the months are in 2027 so they never meet another suite's plan.
@Transactional
@AutoConfigureMockMvc
class NotificationServiceTest extends AbstractIntegrationTest {

    @MockitoBean DiscordNotifier discord;
    @Autowired NotificationService notificationService;
    @Autowired DueBillService dueBillService;
    @Autowired NotificationLogRepository logRepository;
    @Autowired AccountRepository accountRepository;
    @Autowired PlanMonthRepository planMonthRepository;
    @Autowired MonthItemRepository monthItemRepository;
    @Autowired MonthBalanceRepository monthBalanceRepository;
    @Autowired MockMvc mockMvc;

    private Account chase;

    @BeforeEach
    void setUp() {
        chase = accountRepository.save(Account.builder().name("Chase " + UUID.randomUUID())
                .type(AccountType.CHECKING).openingBalance(BigDecimal.ZERO).paymentAccount(true).build());
        when(discord.enabled()).thenReturn(true);
        when(discord.send(any())).thenReturn(true);
    }

    private PlanMonth month(String yyyyMm) {
        return planMonthRepository.save(PlanMonth.builder().month(YearMonth.parse(yyyyMm).atDay(1)).build());
    }

    private void item(PlanMonth month, String name, ItemKind kind, String amount, Integer day, ItemStatus status) {
        monthItemRepository.save(MonthItem.builder().planMonth(month).account(chase).name(name).kind(kind)
                .amount(amount == null ? null : new BigDecimal(amount)).billingDay(day)
                .inExpenditure(kind.inExpenditure(null, null)).status(status)
                .paidOn(status == ItemStatus.PAID ? month.getMonth() : null).build());
    }

    private void bill(PlanMonth month, String name, String amount, Integer day) {
        item(month, name, ItemKind.EXPENSE, amount, day, ItemStatus.PENDING);
    }

    private DueBillService.Snapshot snapshot(LocalDate today) {
        DueBillService.Snapshot s = dueBillService.snapshot(today);
        return new DueBillService.Snapshot(today, s.bills().stream().filter(r -> r.account().equals(chase.getName())).toList(),
                s.paydays().stream().filter(r -> r.account().equals(chase.getName())).toList(), s.remaining());
    }

    @Test
    void onlyUnpaidBillsWithAnAmountAndADayAreDue() {
        PlanMonth jan = month("2027-01");
        PlanMonth feb = month("2027-02");
        bill(jan, "Late", "40", 25);
        bill(feb, "Card", "100", 31);
        bill(feb, "Blank", null, 5);
        bill(feb, "No day", "50", null);
        item(feb, "Paid", ItemKind.EXPENSE, "30", 10, ItemStatus.PAID);
        item(feb, "Salary", ItemKind.INCOME, "2000", 20, ItemStatus.PENDING);
        monthBalanceRepository.save(MonthBalance.builder().planMonth(feb).account(chase).startingBalance(new BigDecimal("500")).build());

        DueBillService.Snapshot s = snapshot(LocalDate.of(2027, 2, 15));

        assertThat(s.bills()).extracting(Row::name, Row::due).containsExactly(
                tuple("Late", LocalDate.of(2027, 1, 25)),
                tuple("Card", LocalDate.of(2027, 2, 28)));
        assertThat(s.paydays()).extracting(Row::name).containsExactly("Salary");
        // 500 + 2000 pay - (100 + 50 + 30) outgoing; the blank row counts as zero.
        assertThat(s.remaining().get(chase.getName())).isEqualByComparingTo("2320");
    }

    @Test
    void nextMonthCountsOnlyOnceItHasBeenOpened() {
        month("2027-02");
        LocalDate today = LocalDate.of(2027, 2, 27);
        assertThat(snapshot(today).bills()).isEmpty();
        assertThat(planMonthRepository.findByMonth(LocalDate.of(2027, 3, 1))).isEmpty();

        bill(month("2027-03"), "Rent", "1850", 1);

        assertThat(snapshot(today).bills()).extracting(Row::name).containsExactly("Rent");
    }

    @Test
    void theDigestGoesOutOnceADay() {
        bill(month("2027-05"), "Rent", "1850", 10);
        LocalDate today = LocalDate.of(2027, 5, 10);

        notificationService.sendDigest(today);
        notificationService.sendDigest(today);

        verify(discord, times(1)).send(any(Message.class));
        assertThat(logRepository.existsByTypeAndRef(NotificationService.DIGEST, "2027-05-10")).isTrue();
    }

    @Test
    void aFailedSendIsTriedAgainOnTheNextRun() {
        bill(month("2027-05"), "Rent", "1850", 10);
        LocalDate today = LocalDate.of(2027, 5, 10);
        when(discord.send(any())).thenReturn(false);

        notificationService.sendDigest(today);
        assertThat(logRepository.existsByTypeAndRef(NotificationService.DIGEST, "2027-05-10")).isFalse();

        notificationService.sendDigest(today);
        verify(discord, times(2)).send(any(Message.class));
    }

    @Test
    void nothingIsClaimedWithoutAWebhook() {
        when(discord.enabled()).thenReturn(false);

        notificationService.sendDigest(LocalDate.of(2027, 5, 10));

        verify(discord, never()).send(any());
        assertThat(logRepository.existsByTypeAndRef(NotificationService.DIGEST, "2027-05-10")).isFalse();
    }

    @Test
    void startingAfterTheSendTimeCatchesUp() {
        String cron = "0 0 8 * * *";
        assertThat(NotificationService.missed(LocalDateTime.of(2027, 5, 10, 7, 59), cron)).isFalse();
        assertThat(NotificationService.missed(LocalDateTime.of(2027, 5, 10, 8, 0), cron)).isTrue();
        assertThat(NotificationService.missed(LocalDateTime.of(2027, 5, 10, 22, 30), cron)).isTrue();
        // Mondays only: a Tuesday start has nothing to catch up on.
        assertThat(NotificationService.missed(LocalDateTime.of(2027, 5, 11, 9, 0), "0 0 8 * * MON")).isFalse();
    }

    @Test
    void testEndpointSaysWhetherDiscordTookIt() throws Exception {
        mockMvc.perform(post("/api/notifications/test")).andExpect(status().isNoContent());

        when(discord.send(any())).thenReturn(false);
        mockMvc.perform(post("/api/notifications/test")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Discord didn't accept the message. Check the webhook URL."));

        when(discord.enabled()).thenReturn(false);
        mockMvc.perform(post("/api/notifications/digest")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Set DISCORD_WEBHOOK_URL to send notifications"));
    }
}
