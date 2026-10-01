package com.expence.expenceApi.model.finance.rest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** One month of the income-vs-expense chart. {@code month} is {@code "YYYY-MM"}; {@code expenses} is a positive magnitude. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MonthlyPointDTO {

    private String month;
    private BigDecimal income;
    private BigDecimal expenses;
}
