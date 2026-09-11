package com.expence.expenceApi.model.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of the ledger. {@code amount} carries its own sign -- INCOME positive,
 * EXPENSE negative, a TRANSFER's legs {@code -x} and {@code +x} -- so summing money
 * never has to branch on {@code type}. The database enforces that sign, that the amount
 * is non-zero, and that a TRANSFER row is uncategorised and grouped.
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Transaction {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	/**
	 * EAGER, not the JPA default LAZY: every read path maps a transaction to a DTO
	 * carrying {@code accountName}, so a lazy proxy would be initialised immediately
	 * anyway -- once per row, which is the N+1 this avoids on the paged list.
	 */
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "account_id")
	private Account account;

	/** EAGER for the same reason as {@code account}: the DTO carries {@code categoryName}. */
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "category_id")
	private Category category;

	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	private TransactionType type;

	@Column(name = "occurred_on")
	private LocalDate occurredOn;

	private String description;

	/** Shared by a transfer's two legs; null on every non-transfer row. */
	@Column(name = "transfer_group_id")
	private UUID transferGroupId;

	/** Maintained by Postgres (column DEFAULT); Hibernate only reads it back. */
	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	/** Maintained by the {@code set_updated_at()} trigger; Hibernate only reads it back. */
	@Column(name = "updated_at", insertable = false, updatable = false)
	private Instant updatedAt;
}
