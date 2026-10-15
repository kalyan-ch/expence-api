package com.expence.expenceApi.dao;

import com.expence.expenceApi.model.finance.RecurringItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecurringItemRepository extends JpaRepository<RecurringItem, UUID> {

	/** The sheet's row order; name breaks ties so the list is stable. */
	List<RecurringItem> findAllByOrderBySortOrderAscNameAsc();

	/** Bill names are unique; the check ignores case so "Rent" and "rent" clash. */
	boolean existsByNameIgnoreCase(String name);

	/** Same check for a rename: the item being renamed is not its own duplicate. */
	boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
