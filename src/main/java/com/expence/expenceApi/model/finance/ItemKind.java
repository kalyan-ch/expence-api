package com.expence.expenceApi.model.finance;

/**
 * What a plan row does to its checking account. INCOME adds; the other three take money
 * out. EXPENSE and CARD_PAYMENT count toward the sheet's Expenditure; SAVINGS does not.
 */
public enum ItemKind {
	INCOME, EXPENSE, CARD_PAYMENT, SAVINGS
}
