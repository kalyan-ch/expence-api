package com.expence.expenceApi.model.finance.rest;

import com.expence.expenceApi.model.finance.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read model for an account. {@code balance} is derived per request as
 * {@code openingBalance + sum(transactions)} and never stored.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountDTO {

    private UUID id;
    private String name;
    private AccountType type;
    private BigDecimal openingBalance;
    private BigDecimal balance;
}
