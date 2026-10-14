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
 * One row of a month's sheet. Name, kind, account and amount are copied from the
 * recurring item when the month is created, so editing this month never rewrites the
 * template or past months. {@code recurringItem} is null for a one-off row.
 */
@Entity
@Table(name = "month_items")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MonthItem {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "month_id")
	private PlanMonth planMonth;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recurring_item_id")
	private RecurringItem recurringItem;

	private String name;

	@Enumerated(EnumType.STRING)
	private ItemKind kind;

	/** EAGER: every row on the sheet shows its From Account. */
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "account_id")
	private Account account;

	/** Positive magnitude, or null for a blank cell (nothing due this month). */
	private BigDecimal amount;

	@Column(name = "billing_day")
	private Integer billingDay;

	@Column(name = "sort_order")
	private int sortOrder;

	@Builder.Default
	@Enumerated(EnumType.STRING)
	private ItemStatus status = ItemStatus.PENDING;

	@Column(name = "paid_on")
	private LocalDate paidOn;

	/** Maintained by Postgres (column DEFAULT); Hibernate only reads it back. */
	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	/** Maintained by the {@code set_updated_at()} trigger; Hibernate only reads it back. */
	@Column(name = "updated_at", insertable = false, updatable = false)
	private Instant updatedAt;
}
