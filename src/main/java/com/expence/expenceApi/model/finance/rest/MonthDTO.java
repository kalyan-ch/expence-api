package com.expence.expenceApi.model.finance.rest;

import com.expence.expenceApi.model.finance.ItemKind;
import com.expence.expenceApi.model.finance.ItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * One month's sheet: its rows sorted by billing day, the account panel, and the totals
 * under the bills table. Every total is computed per request and never stored.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MonthDTO {

    private YearMonth month;
    private List<Item> items;
    private List<AccountTotals> accounts;

    /** EXPENSE + CARD_PAYMENT + SAVINGS: the sheet's Total. */
    private BigDecimal total;

    /** Total without savings: the sheet's Expenditure and the trend chart's bar. */
    private BigDecimal expenditure;

    /** Outgoing rows with an amount that are not yet paid. */
    private int unpaidCount;
    private BigDecimal unpaidTotal;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Item {
        private UUID id;
        private UUID recurringItemId;
        private String name;
        private ItemKind kind;
        private UUID accountId;
        private String accountName;
        private BigDecimal amount;
        private Integer billingDay;
        private int sortOrder;
        private ItemStatus status;
        private LocalDate paidOn;
    }

    /** One row of the sheet's right-hand table. Remaining = startingBalance + income - expenses. */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AccountTotals {
        private UUID accountId;
        private String accountName;
        /** Null until typed in from the bank app; counts as zero in Remaining. */
        private BigDecimal startingBalance;
        private BigDecimal income;
        private BigDecimal expenses;
        private BigDecimal remaining;
    }
}
