package com.expence.expenceApi.model.finance.rest;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** The balance typed in from the bank app. May be negative (overdrawn). */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MonthBalanceRequest {

    @NotNull
    @Digits(integer = 15, fraction = 4)
    private BigDecimal startingBalance;
}
