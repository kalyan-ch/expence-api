package com.expence.expenceApi.dao;

import com.expence.expenceApi.model.finance.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

	/** Account names are unique; the check ignores case so "Cash" and "cash" clash. */
	boolean existsByNameIgnoreCase(String name);

	/** Same check for a rename: the account being renamed is not its own duplicate. */
	boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
