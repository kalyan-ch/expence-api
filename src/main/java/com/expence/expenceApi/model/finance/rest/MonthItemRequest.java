package com.expence.expenceApi.model.finance.rest;

import com.expence.expenceApi.model.finance.ItemKind;
import com.expence.expenceApi.model.finance.ItemStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Body for adding a one-off row to a month or editing one of its rows. Edits never touch
 * the recurring item. {@code status} null means PENDING; {@code paidOn} defaults to today
 * when PAID and is cleared when PENDING.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MonthItemRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    private ItemKind kind;

    @NotNull
    private UUID accountId;

    @Positive
    @Digits(integer = 15, fraction = 4)
    private BigDecimal amount;

    @Min(1)
    @Max(31)
    private Integer billingDay;

    private ItemStatus status;

    private LocalDate paidOn;
}
