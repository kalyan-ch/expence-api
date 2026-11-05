package com.expence.expenceApi.service;

import com.expence.expenceApi.model.finance.ItemKind;
import com.expence.expenceApi.model.finance.ItemStatus;
import com.expence.expenceApi.model.finance.rest.MonthDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Reads what the reminders need from last month, this month and next month, without opening any month. */
@Service
@RequiredArgsConstructor
public class DueBillService {

    public record Row(String account, String name, BigDecimal amount, LocalDate due) {}

    /**
     * {@code bills}: unpaid outgoing rows with an amount and a day, oldest first.
     * {@code paydays}: paychecks after today. {@code remaining}: this month's forecast for accounts with a starting balance.
     */
    public record Snapshot(LocalDate today, List<Row> bills, List<Row> paydays, Map<String, BigDecimal> remaining) {}

    private static final Comparator<Row> BY_DUE = Comparator.comparing(Row::due).thenComparing(Row::name);

    private final MonthService monthService;

    public Snapshot snapshot(LocalDate today) {
        YearMonth now = YearMonth.from(today);
        List<MonthDTO> months = Stream.of(now.minusMonths(1), now, now.plusMonths(1))
                .map(monthService::find).flatMap(Optional::stream).toList();
        List<Row> bills = rows(months, i -> i.getKind() != ItemKind.INCOME && i.getStatus() == ItemStatus.PENDING);
        List<Row> paydays = rows(months, i -> i.getKind() == ItemKind.INCOME).stream().filter(r -> r.due().isAfter(today)).toList();
        Map<String, BigDecimal> remaining = months.stream().filter(m -> m.getMonth().equals(now))
                .flatMap(m -> m.getAccounts().stream())
                .filter(a -> a.getStartingBalance() != null)
                .collect(Collectors.toMap(MonthDTO.AccountTotals::getAccountName, MonthDTO.AccountTotals::getRemaining));
        return new Snapshot(today, bills, paydays, remaining);
    }

    /** A billing day past the month's end (31 in February) falls on its last day. */
    static LocalDate dueDate(YearMonth month, int day) {
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }

    /** A blank or zero amount means nothing is due; a row with no day can't be dated. */
    private static List<Row> rows(List<MonthDTO> months, Predicate<MonthDTO.Item> filter) {
        return months.stream()
                .flatMap(m -> m.getItems().stream()
                        .filter(i -> i.getBillingDay() != null && i.getAmount() != null && i.getAmount().signum() > 0)
                        .filter(filter)
                        .map(i -> new Row(i.getAccountName(), i.getName(), i.getAmount(), dueDate(m.getMonth(), i.getBillingDay()))))
                .sorted(BY_DUE)
                .toList();
    }
}
