package com.expence.expenceApi.model.finance;

/**
 * The kinds of account Phase 1 tracks. Stored as TEXT and constrained by a CHECK in
 * the accounts migration rather than a native Postgres enum, so adding a type later is
 * a one-line constraint change instead of an ALTER TYPE.
 */
public enum AccountType {
	CHECKING, SAVINGS, CREDIT_CARD, CASH
}
