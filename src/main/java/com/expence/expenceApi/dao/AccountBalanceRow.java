package com.expence.expenceApi.dao;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One account's signed transaction total, as returned by
 * {@link TransactionRepository#sumGroupedByAccount()}. A Spring Data interface
 * projection: the JPQL constructor-free {@code select ... group by} binds to these
 * getters by alias.
 */
public interface AccountBalanceRow {

	UUID getAccountId();

	BigDecimal getTotal();
}
