package com.expence.expenceApi.model.finance.rest;

import com.expence.expenceApi.model.finance.AccountType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    private AccountType type;

    @NotNull
    @Digits(integer = 15, fraction = 4)
    private BigDecimal openingBalance;
}
