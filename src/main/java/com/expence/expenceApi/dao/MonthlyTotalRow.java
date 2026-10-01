package com.expence.expenceApi.dao;

import java.math.BigDecimal;

/**
 * One month's income and expense totals, as returned by
 * {@link ReportRepository#monthlyTotals}. {@code month} is {@code "YYYY-MM"}; both
 * totals are positive magnitudes.
 */
public interface MonthlyTotalRow {

	String getMonth();

	BigDecimal getIncome();

	BigDecimal getExpenses();
}
