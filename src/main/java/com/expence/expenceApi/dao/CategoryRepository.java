package com.expence.expenceApi.dao;

import com.expence.expenceApi.model.finance.Category;
import com.expence.expenceApi.model.finance.CategoryKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

	/** Backs {@code GET /api/categories?kind=...}. */
	List<Category> findAllByKindOrderByNameAsc(CategoryKind kind);

	/** Backs the unfiltered list: income group first, alphabetical within each kind. */
	List<Category> findAllByOrderByKindAscNameAsc();

	/** A name is unique only within its kind, so both must match to be a duplicate. */
	boolean existsByNameIgnoreCaseAndKind(String name, CategoryKind kind);

	/** Same check for a rename, excluding the category being renamed. */
	boolean existsByNameIgnoreCaseAndKindAndIdNot(String name, CategoryKind kind, UUID id);
}
