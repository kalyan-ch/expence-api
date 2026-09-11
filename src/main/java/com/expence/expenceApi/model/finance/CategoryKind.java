package com.expence.expenceApi.model.finance;

/**
 * Whether a category classifies money coming in or going out. A category's kind is
 * immutable after creation, and an INCOME transaction may only reference an INCOME
 * category (enforced in the service layer -- the database cannot express that check
 * across tables).
 */
public enum CategoryKind {
	INCOME, EXPENSE
}
