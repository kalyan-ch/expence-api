package com.expence.expenceApi.model.finance.rest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.YearMonth;

/** One entry of the month switcher and one bar of the expenditure trend chart. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MonthSummaryDTO {

    private YearMonth month;
    private BigDecimal total;
    private BigDecimal expenditure;
}
