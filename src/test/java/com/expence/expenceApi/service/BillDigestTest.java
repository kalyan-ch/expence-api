package com.expence.expenceApi.service;

import com.expence.expenceApi.service.DiscordNotifier.Field;
import com.expence.expenceApi.service.DiscordNotifier.Message;
import com.expence.expenceApi.service.DueBillService.Row;
import com.expence.expenceApi.service.DueBillService.Snapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class BillDigestTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);
    private static final String APP = "http://localhost:3000";

    private static Row row(String account, String name, String amount, int day) {
        return new Row(account, name, new BigDecimal(amount), LocalDate.of(2026, 11, day));
    }

    @Test
    void listsOverdueTodayAndUpcomingThenWhatEachAccountMustCover() {
        Snapshot snapshot = new Snapshot(TODAY,
                List.of(row("DCU", "Amex", "412.10", 1), row("Chase", "Rent", "1850", 3), row("Chase", "Sapphire", "230.55", 5),
                        row("DCU", "Car", "389", 6), row("Chase", "Freedom", "99", 20)),
                List.of(row("Chase", "Salary", "1985.72", 13)),
                Map.of("Chase", new BigDecimal("4462.09")));

        Message message = BillDigest.build(snapshot, 3, APP).orElseThrow();

        assertThat(message.title()).isEqualTo("💸 Bills — Tue 3 Nov");
        assertThat(message.url()).isEqualTo(APP + "/bills");
        assertThat(message.fields()).extracting(Field::name).containsExactly("🔴 Overdue", "🟠 Due today", "🟡 Next 3 days", "Accounts");
        assertThat(message.fields()).extracting(Field::value).containsExactly(
                "Amex · $412.10 · DCU · was due Sun 1 Nov (2 days)",
                "Rent · $1,850.00 · Chase",
                "Sapphire · $230.55 · Chase · Thu 5 Nov\nCar · $389.00 · DCU · Fri 6 Nov",
                "**Chase**: $2,080.55 due before next pay (Fri 13 Nov) · forecast left $4,462.09\n**DCU**: $801.10 due by month end");
    }

    @Test
    void sectionsWithNothingInThemAreLeftOut() {
        Snapshot snapshot = new Snapshot(TODAY, List.of(row("Chase", "Rent", "1850", 3)), List.of(), Map.of());

        assertThat(BillDigest.build(snapshot, 3, APP).orElseThrow().fields()).extracting(Field::name).containsExactly("🟠 Due today", "Accounts");
    }

    @Test
    void nothingIsSentWhenNoBillIsDueSoon() {
        Snapshot snapshot = new Snapshot(TODAY, List.of(row("Chase", "Freedom", "99", 20)), List.of(), Map.of());

        assertThat(BillDigest.build(snapshot, 3, APP)).isEmpty();
    }

    @Test
    void longSectionsAreCutToDiscordsFieldLimit() {
        List<String> lines = IntStream.range(0, 60).mapToObj(i -> "Bill number " + i + " · $100.00 · Chase").toList();

        String value = BillDigest.fit(lines);

        assertThat(value.length()).isLessThanOrEqualTo(BillDigest.FIELD_LIMIT);
        assertThat(value).startsWith("Bill number 0").matches("(?s).*\\n… and \\d+ more");
        assertThat(BillDigest.fit(List.of("a", "b"))).isEqualTo("a\nb");
    }
}
