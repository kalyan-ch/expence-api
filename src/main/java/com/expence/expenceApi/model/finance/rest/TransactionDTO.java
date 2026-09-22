package com.expence.expenceApi.model.finance.rest;

import com.expence.expenceApi.model.finance.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Read model for a transaction. {@code amount} is signed as stored, not the magnitude the
 * request carried. A non-null {@code transferGroupId} marks a transfer leg, which is how
 * the UI knows to block inline editing and send the user to the transfer instead.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionDTO {

    private UUID id;
    private UUID accountId;
    private String accountName;
    private UUID categoryId;
    private String categoryName;
    private BigDecimal amount;
    private TransactionType type;
    private LocalDate occurredOn;
    private String description;
    private UUID transferGroupId;
}
