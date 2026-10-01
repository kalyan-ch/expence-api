package com.expence.expenceApi.dao;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One category's total over a date range, as returned by
 * {@link ReportRepository#categoryTotals}. {@code total} is a positive magnitude.
 */
public interface CategoryTotalRow {

	UUID getCategoryId();

	String getCategoryName();

	BigDecimal getTotal();
}
