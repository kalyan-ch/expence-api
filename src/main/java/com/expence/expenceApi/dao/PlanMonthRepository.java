package com.expence.expenceApi.dao;

import com.expence.expenceApi.model.finance.PlanMonth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanMonthRepository extends JpaRepository<PlanMonth, UUID> {

	/** {@code month} is the first day of the month. */
	Optional<PlanMonth> findByMonth(LocalDate month);

	/** Oldest first: the order the trend chart draws. */
	List<PlanMonth> findAllByOrderByMonthAsc();
}
