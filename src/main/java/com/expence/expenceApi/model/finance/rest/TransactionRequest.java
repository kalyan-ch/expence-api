package com.expence.expenceApi.model.finance.rest;

import com.expence.expenceApi.model.finance.TransactionType;
import jakarta.validation.constraints.Digits;
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
 * Write model for a transaction. {@code amount} is a positive magnitude -- the client
 * never sends a sign; the service derives it from {@code type} (negative for EXPENSE)
 * so the ledger's sign convention lives in exactly one place.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionRequest {

    @NotNull
    private UUID accountId;

    /** Optional: an uncategorised transaction is allowed. */
    private UUID categoryId;

    @NotNull
    @Positive
    @Digits(integer = 15, fraction = 4)
    private BigDecimal amount;

    @NotNull
    private TransactionType type;

    @NotNull
    private LocalDate occurredOn;

    @Size(max = 500)
    private String description;
}
