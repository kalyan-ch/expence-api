package com.expence.expenceApi.service;

import com.expence.expenceApi.service.DiscordNotifier.Field;
import com.expence.expenceApi.service.DiscordNotifier.Message;
import com.expence.expenceApi.service.DueBillService.Row;
import com.expence.expenceApi.service.DueBillService.Snapshot;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/** Turns a snapshot into the morning message. Pure: no IO, so it is tested with plain unit tests. */
public final class BillDigest {

    /** Discord's limit on one embed field's value. */
    static final int FIELD_LIMIT = 1024;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    private BillDigest() {}

    /** Empty when nothing is overdue, due today or due within {@code leadDays}. */
    public static Optional<Message> build(Snapshot s, int leadDays, String appUrl) {
        LocalDate today = s.today();
        List<Field> fields = new ArrayList<>();
        section(fields, "🔴 Overdue", s.bills(), r -> r.due().isBefore(today),
                r -> line(r) + " · was due " + DAY.format(r.due()) + " (" + days(ChronoUnit.DAYS.between(r.due(), today)) + ")");
        section(fields, "🟠 Due today", s.bills(), r -> r.due().equals(today), BillDigest::line);
        section(fields, "🟡 Next " + days(leadDays), s.bills(),
                r -> r.due().isAfter(today) && !r.due().isAfter(today.plusDays(leadDays)),
                r -> line(r) + " · " + DAY.format(r.due()));
        if (fields.isEmpty()) return Optional.empty();
        List<String> accounts = s.bills().stream().filter(r -> !r.due().isAfter(today.plusDays(leadDays)))
                .map(Row::account).distinct().sorted().toList();
        fields.add(new Field("Accounts", fit(accounts.stream().map(a -> account(s, a)).toList())));
        return Optional.of(new Message(title(today), appUrl + "/bills", fields));
    }

    /** Sent on demand when there is nothing to remind about, so the output can still be checked. */
    public static Message allClear(LocalDate today, int leadDays, String appUrl) {
        return new Message(title(today), appUrl + "/bills", List.of(new Field("✅ All clear", "Nothing due in the next " + days(leadDays) + ".")));
    }

    private static String title(LocalDate today) {
        return "💸 Bills — " + DAY.format(today);
    }

    private static void section(List<Field> fields, String name, List<Row> bills, Predicate<Row> filter, Function<Row, String> format) {
        List<String> lines = bills.stream().filter(filter).map(format).toList();
        if (!lines.isEmpty()) fields.add(new Field(name, fit(lines)));
    }

    private static String line(Row r) {
        return r.name() + " · " + money(r.amount()) + " · " + r.account();
    }

    /** What the account must cover before its next paycheck lands, or by month end if no pay is planned. */
    private static String account(Snapshot s, String account) {
        Optional<LocalDate> nextPay = s.paydays().stream().filter(p -> p.account().equals(account)).map(Row::due).findFirst();
        LocalDate monthEnd = YearMonth.from(s.today()).atEndOfMonth();
        BigDecimal due = s.bills().stream()
                .filter(r -> r.account().equals(account))
                .filter(r -> nextPay.map(r.due()::isBefore).orElse(!r.due().isAfter(monthEnd)))
                .map(Row::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        String line = "**" + account + "**: " + money(due) + nextPay.map(p -> " due before next pay (" + DAY.format(p) + ")").orElse(" due by month end");
        BigDecimal left = s.remaining().get(account);
        return left == null ? line : line + " · forecast left " + money(left);
    }

    /** Keeps whole lines within Discord's field limit and says how many were cut. */
    static String fit(List<String> lines) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String more = "\n… and " + (lines.size() - i) + " more";
            String next = (i == 0 ? "" : "\n") + lines.get(i);
            boolean last = i == lines.size() - 1;
            if (out.length() + next.length() + (last ? 0 : more.length()) > FIELD_LIMIT) return (out + more).strip();
            out.append(next);
        }
        return out.toString();
    }

    private static String days(long n) {
        return n + (n == 1 ? " day" : " days");
    }

    private static String money(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.US).format(amount);
    }
}
