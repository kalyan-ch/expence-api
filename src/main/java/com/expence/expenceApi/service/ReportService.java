package com.expence.expenceApi.service;

import com.expence.expenceApi.dao.MonthlyTotalRow;
import com.expence.expenceApi.dao.ReportRepository;
import com.expence.expenceApi.model.finance.CategoryKind;
import com.expence.expenceApi.model.finance.rest.CategoryTotalDTO;
import com.expence.expenceApi.model.finance.rest.MonthlyPointDTO;
import com.expence.expenceApi.model.finance.rest.SummaryDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final AccountService accountService;

    @Transactional(readOnly = true)
    public SummaryDTO summary(YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        BigDecimal income = reportRepository.incomeBetween(from, to);
        BigDecimal expenses = reportRepository.expensesBetween(from, to);
        return new SummaryDTO(month.toString(), accountService.totalBalance(),
                income, expenses, income.subtract(expenses));
    }

    /** One point per month from {@code from} to {@code to}, zero-filled so charts have no gaps. */
    @Transactional(readOnly = true)
    public List<MonthlyPointDTO> monthly(LocalDate from, LocalDate to) {
        Map<String, MonthlyTotalRow> rows = reportRepository.monthlyTotals(from, to).stream()
                .collect(Collectors.toMap(MonthlyTotalRow::getMonth, Function.identity()));
        List<MonthlyPointDTO> points = new ArrayList<>();
        for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)); m = m.plusMonths(1)) {
            MonthlyTotalRow row = rows.get(m.toString());
            points.add(row == null
                    ? new MonthlyPointDTO(m.toString(), BigDecimal.ZERO, BigDecimal.ZERO)
                    : new MonthlyPointDTO(m.toString(), row.getIncome(), row.getExpenses()));
        }
        return points;
    }

    @Transactional(readOnly = true)
    public List<CategoryTotalDTO> byCategory(LocalDate from, LocalDate to, CategoryKind kind) {
        return reportRepository.categoryTotals(from, to, kind.name()).stream()
                .map(r -> new CategoryTotalDTO(r.getCategoryId(), r.getCategoryName(), r.getTotal()))
                .toList();
    }
}
