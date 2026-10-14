package com.expence.expenceApi.dao;

import com.expence.expenceApi.model.finance.MonthBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MonthBalanceRepository extends JpaRepository<MonthBalance, UUID> {
}
