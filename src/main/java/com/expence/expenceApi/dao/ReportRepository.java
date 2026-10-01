package com.expence.expenceApi.dao;

import com.expence.expenceApi.model.finance.Transaction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only aggregates over the ledger. Native Postgres because JPQL cannot express
 * {@code to_char} month bucketing or {@code FILTER (WHERE ...)} aggregates portably.
 * Every query excludes transfer legs ({@code transfer_group_id IS NULL}): moving money
 * between accounts is neither income nor expense. Date ranges are inclusive.
 *
 * <p>Extends the bare {@link Repository} marker, not {@code JpaRepository}: reports never
 * write, so no CRUD methods are exposed.
 */
public interface ReportRepository extends Repository<Transaction, UUID> {

	@Query(value = """
			SELECT COALESCE(SUM(amount), 0) FROM transactions
			WHERE type = 'INCOME' AND transfer_group_id IS NULL
			  AND occurred_on BETWEEN :from AND :to
			""", nativeQuery = true)
	BigDecimal incomeBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

	/** Positive magnitude: EXPENSE rows are stored negative. */
	@Query(value = """
			SELECT ABS(COALESCE(SUM(amount), 0)) FROM transactions
			WHERE type = 'EXPENSE' AND transfer_group_id IS NULL
			  AND occurred_on BETWEEN :from AND :to
			""", nativeQuery = true)
	BigDecimal expensesBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

	/** Only months with activity; the service zero-fills the gaps. Aliases quoted so Postgres keeps their case. */
	@Query(value = """
			SELECT to_char(occurred_on, 'YYYY-MM') AS "month",
			       COALESCE(SUM(amount) FILTER (WHERE type = 'INCOME'), 0) AS "income",
			       ABS(COALESCE(SUM(amount) FILTER (WHERE type = 'EXPENSE'), 0)) AS "expenses"
			FROM transactions
			WHERE transfer_group_id IS NULL AND occurred_on BETWEEN :from AND :to
			GROUP BY 1
			ORDER BY 1
			""", nativeQuery = true)
	List<MonthlyTotalRow> monthlyTotals(@Param("from") LocalDate from, @Param("to") LocalDate to);

	/** Uncategorised rows have no bucket to land in, so the inner join drops them. */
	@Query(value = """
			SELECT c.id AS "categoryId", c.name AS "categoryName", ABS(SUM(t.amount)) AS "total"
			FROM transactions t
			JOIN categories c ON c.id = t.category_id
			WHERE t.transfer_group_id IS NULL AND c.kind = :kind
			  AND t.occurred_on BETWEEN :from AND :to
			GROUP BY c.id, c.name
			ORDER BY 3 DESC
			""", nativeQuery = true)
	List<CategoryTotalRow> categoryTotals(@Param("from") LocalDate from, @Param("to") LocalDate to,
			@Param("kind") String kind);
}
