package com.expence.expenceApi.model.finance;

/**
 * How often a recurring item lands. MONTHLY and TWICE_MONTHLY fall on fixed days of the month;
 * BIWEEKLY and WEEKLY count from an anchor payday, so a month can get 3 or 5 of them.
 */
public enum ItemFrequency {
	MONTHLY, TWICE_MONTHLY, BIWEEKLY, WEEKLY
}
