package com.expence.expenceApi.model.finance.rest;

import com.expence.expenceApi.model.finance.ItemKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/** Read model for a recurring bill or paycheck: one row of the sheet's template. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RecurringItemDTO {

    private UUID id;
    private String name;
    private ItemKind kind;
    private UUID accountId;
    private String accountName;
    private BigDecimal defaultAmount;
    private Integer billingDay;
    private int sortOrder;
    private boolean active;
}
