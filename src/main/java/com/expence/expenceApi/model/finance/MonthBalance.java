package com.expence.expenceApi.model.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The sheet's Balance column: typed in from the bank app at the start of the month,
 * once per account. Not derived from transactions -- the plan tracks bills only.
 */
@Entity
@Table(name = "month_balances")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MonthBalance {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "month_id")
	private PlanMonth planMonth;

	/** EAGER: the account panel always shows the account name. */
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "account_id")
	private Account account;

	@Column(name = "starting_balance")
	private BigDecimal startingBalance;

	/** Maintained by Postgres (column DEFAULT); Hibernate only reads it back. */
	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	/** Maintained by the {@code set_updated_at()} trigger; Hibernate only reads it back. */
	@Column(name = "updated_at", insertable = false, updatable = false)
	private Instant updatedAt;
}
