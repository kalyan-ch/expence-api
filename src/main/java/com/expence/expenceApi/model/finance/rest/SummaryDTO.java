package com.expence.expenceApi.model.finance.rest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Dashboard headline figures. {@code totalBalance} spans every account for all time and
 * ignores {@code month}; the rest cover {@code month} only. {@code income} and
 * {@code expenses} are positive magnitudes; {@code savings = income - expenses} and is
 * negative in a month that spent more than it earned.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SummaryDTO {

    private String month;
    private BigDecimal totalBalance;
    private BigDecimal income;
    private BigDecimal expenses;
    private BigDecimal savings;
}
