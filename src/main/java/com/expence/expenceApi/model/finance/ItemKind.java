package com.expence.expenceApi.model.finance;

/**
 * What a plan row does to its checking account. INCOME adds; the other three take money
 * out. Whether a bill counts toward Expenditure is chosen per bill; by default EXPENSE and
 * CARD_PAYMENT do and SAVINGS does not.
 */
public enum ItemKind {
	INCOME, EXPENSE, CARD_PAYMENT, SAVINGS;

	/** As requested, else unchanged ({@code current}, null for a new row), else by kind. Pay never counts. */
	public boolean inExpenditure(Boolean requested, Boolean current) {
		if (this == INCOME) return false;
		if (requested != null) return requested;
		return current != null ? current : this == EXPENSE || this == CARD_PAYMENT;
	}
}
