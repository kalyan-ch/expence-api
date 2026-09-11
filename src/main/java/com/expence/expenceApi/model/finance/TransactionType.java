package com.expence.expenceApi.model.finance;

/**
 * What a ledger row represents. The type fixes the sign of {@code amount}: INCOME is
 * positive, EXPENSE negative, and a TRANSFER's two legs are {@code -x} and {@code +x}.
 * TRANSFER rows are neither income nor expense, so every report query excludes them.
 */
public enum TransactionType {
	INCOME, EXPENSE, TRANSFER
}
