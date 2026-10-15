package com.expence.expenceApi.dao;

import com.expence.expenceApi.model.finance.MonthItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MonthItemRepository extends JpaRepository<MonthItem, UUID> {

	List<MonthItem> findAllByPlanMonthId(UUID planMonthId);

	/** How many months copied this recurring item; a used item can only be retired. */
	long countByRecurringItemId(UUID recurringItemId);
}
