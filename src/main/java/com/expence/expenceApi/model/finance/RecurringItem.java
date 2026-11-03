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
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

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

	@Enumerated(EnumType.STRING)
	@Builder.Default
	private ItemFrequency frequency = ItemFrequency.MONTHLY;
	/** TWICE_MONTHLY only: the second fixed day. */
	@Column(name = "second_day")
	private Integer secondDay;
	/** BIWEEKLY and WEEKLY only: any known payday; the others are counted from it. */
	@Column(name = "anchor_date")
	private LocalDate anchorDate;
	/** Counts toward the month's Expenditure. */
	@Column(name = "in_expenditure")
	private boolean inExpenditure;
	@Column(name = "sort_order")
	private int sortOrder;

	@Builder.Default
	private boolean active = true;

	/** The days this item lands on in {@code month}; one null day for a monthly item with no fixed day. */
	public List<Integer> daysIn(YearMonth month) {
		return switch (frequency) {
			case MONTHLY -> Collections.singletonList(billingDay);
			case TWICE_MONTHLY -> List.of(billingDay, secondDay);
			case BIWEEKLY, WEEKLY -> {
				int step = frequency == ItemFrequency.WEEKLY ? 7 : 14;
				LocalDate start = month.atDay(1);
				LocalDate first = start.plusDays(Math.floorMod(ChronoUnit.DAYS.between(start, anchorDate), step));
				yield Stream.iterate(first, d -> !d.isAfter(month.atEndOfMonth()), d -> d.plusDays(step))
						.map(LocalDate::getDayOfMonth)
						.toList();
			}
		};
	}

	/** Maintained by Postgres (column DEFAULT); Hibernate only reads it back. */
	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	/** Maintained by the {@code set_updated_at()} trigger; Hibernate only reads it back. */
	@Column(name = "updated_at", insertable = false, updatable = false)
	private Instant updatedAt;
}
