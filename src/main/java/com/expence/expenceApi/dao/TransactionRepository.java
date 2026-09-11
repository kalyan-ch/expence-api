package com.expence.expenceApi.dao;

import com.expence.expenceApi.model.finance.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID>,
		JpaSpecificationExecutor<Transaction> {

	/**
	 * Signed total for one account. {@code coalesce(..., 0)} so an account with no
	 * transactions returns zero rather than null and the caller never null-checks
	 * before adding it to the opening balance.
	 */
	@Query("select coalesce(sum(t.amount), 0) from Transaction t where t.account.id = :accountId")
	BigDecimal sumByAccountId(@Param("accountId") UUID accountId);

	/**
	 * Every account's signed total in one query. Listing accounts needs a balance per
	 * row; without this it would call {@link #sumByAccountId} once per account, which
	 * is the N+1 this exists to avoid.
	 */
	@Query("""
			select t.account.id as accountId, coalesce(sum(t.amount), 0) as total
			from Transaction t
			group by t.account.id
			""")
	List<AccountBalanceRow> sumGroupedByAccount();

	/** Deleting an account is refused while it has transactions; this names the count. */
	long countByAccountId(UUID accountId);

	/** Deleting a category is refused while it is in use; this names the count. */
	long countByCategoryId(UUID categoryId);

	/** Both legs of a transfer, for editing or deleting it as one unit. */
	List<Transaction> findAllByTransferGroupId(UUID transferGroupId);

	/** CSV import skips a row that already exists on these four fields. */
	boolean existsByAccountIdAndOccurredOnAndAmountAndDescription(
			UUID accountId, LocalDate occurredOn, BigDecimal amount, String description);
}
