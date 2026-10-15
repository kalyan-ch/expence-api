package com.expence.expenceApi.model.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * A place money sits. There is deliberately no {@code balance} field: the live balance
 * is {@code openingBalance + COALESCE(SUM(amount), 0)} over this account's transactions,
 * computed per request. A stored balance could drift from the ledger and would need
 * cache invalidation and a repair job; a derived one cannot.
 */
@Entity
@Table(name = "accounts")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Account {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String name;

	@Enumerated(EnumType.STRING)
	private AccountType type;

	@Column(name = "opening_balance")
	private BigDecimal openingBalance;

	/** Bills and paychecks can only be assigned to a payment account. */
	@Column(name = "payment_account")
	private boolean paymentAccount;

	/** Maintained by Postgres (column DEFAULT); Hibernate only reads it back. */
	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	/** Maintained by the {@code set_updated_at()} trigger; Hibernate only reads it back. */
	@Column(name = "updated_at", insertable = false, updatable = false)
	private Instant updatedAt;
}
