package com.expence.expenceApi.model.finance.rest;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/** One category's total over a date range. {@code total} is a positive magnitude for both kinds. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CategoryTotalDTO {

    private UUID categoryId;
    private String categoryName;
    private BigDecimal total;
}
