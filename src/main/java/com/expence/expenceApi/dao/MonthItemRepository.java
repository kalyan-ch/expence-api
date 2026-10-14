package com.expence.expenceApi.dao;

import com.expence.expenceApi.model.finance.MonthItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MonthItemRepository extends JpaRepository<MonthItem, UUID> {
}
