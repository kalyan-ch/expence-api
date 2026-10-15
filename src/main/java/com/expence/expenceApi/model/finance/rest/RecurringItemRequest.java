package com.expence.expenceApi.model.finance.rest;

import com.expence.expenceApi.model.finance.ItemKind;
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
import java.util.UUID;

/**
 * Body for creating or updating a recurring bill or paycheck. {@code defaultAmount} and
 * {@code billingDay} may be null: a blank cell in the sheet. {@code sortOrder} and
 * {@code active} are optional; null keeps the current value (0 and true on create).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RecurringItemRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    private ItemKind kind;

    @NotNull
    private UUID accountId;

    @Positive
    @Digits(integer = 15, fraction = 4)
    private BigDecimal defaultAmount;

    @Min(1)
    @Max(31)
    private Integer billingDay;

    private Integer sortOrder;

    private Boolean active;
}
