package com.expence.expenceApi.controller;

import com.expence.expenceApi.model.finance.CategoryKind;
import com.expence.expenceApi.model.finance.rest.CategoryTotalDTO;
import com.expence.expenceApi.model.finance.rest.MonthlyPointDTO;
import com.expence.expenceApi.model.finance.rest.SummaryDTO;
import com.expence.expenceApi.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/summary")
    public SummaryDTO summary(@RequestParam String month) {
        return reportService.summary(parseMonth(month));
    }

    @GetMapping("/monthly")
    public List<MonthlyPointDTO> monthly(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.monthly(from, to);
    }

    @GetMapping("/by-category")
    public List<CategoryTotalDTO> byCategory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam CategoryKind kind) {
        return reportService.byCategory(from, to, kind);
    }

    /** YearMonth has no Spring converter; rethrown as IllegalArgumentException so the handler maps it to 400. */
    private static YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("month must be in YYYY-MM format");
        }
    }
}
