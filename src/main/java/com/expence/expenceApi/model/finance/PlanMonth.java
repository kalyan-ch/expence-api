package com.expence.expenceApi.model.finance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One planned month -- one copy of the sheet. {@code month} is always the first day;
 * the database rejects any other day.
 */
@Entity
@Table(name = "months")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlanMonth {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private LocalDate month;

	/** Maintained by Postgres (column DEFAULT); Hibernate only reads it back. */
	@Column(name = "created_at", insertable = false, updatable = false)
	private Instant createdAt;

	/** Maintained by the {@code set_updated_at()} trigger; Hibernate only reads it back. */
	@Column(name = "updated_at", insertable = false, updatable = false)
	private Instant updatedAt;
}
