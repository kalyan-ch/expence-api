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
import java.util.UUID;

/**
 * One row of the sheet's template: a bill or paycheck split entered once and copied into
 * every new month. Retiring ({@code active = false}) hides it from future months but keeps
 * the history of months that already copied it.
 */
@Entity
@Table(name = "recurring_items")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RecurringItem {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String name;

	@Enumerated(EnumType.STRING)
	private ItemKind kind;

	/** EAGER: every read maps to a DTO carrying the account name. */
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "account_id")
	private Account account;

	/** Positive magnitude, or null for "varies; fill in each month". */
	@Column(name = "default_amount")
	private BigDecimal defaultAmount;

	@Column(name = "billing_day")
	private Integer billingDay;

	@Column(name = "sort_order")
	private int sortOrder;

	@Builder.Default
	private boolean active = true;

	/** Maintained by Postgres (column DEFAULT); Hibernate only reads it back. */
	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	/** Maintained by the {@code set_updated_at()} trigger; Hibernate only reads it back. */
	@Column(name = "updated_at", insertable = false, updatable = false)
	private Instant updatedAt;
}
