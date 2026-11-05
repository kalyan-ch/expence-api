package com.expence.expenceApi.dao;

import com.expence.expenceApi.model.finance.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

	/** 1 if this caller now owns the send, 0 if it was already claimed. */
	@Modifying
	@Transactional
	@Query(value = "INSERT INTO notification_log (type, ref) VALUES (:type, :ref) ON CONFLICT DO NOTHING", nativeQuery = true)
	int claim(String type, String ref);

	/** Gives a claim back after a failed send, so a later run tries again. */
	@Modifying
	@Transactional
	@Query(value = "DELETE FROM notification_log WHERE type = :type AND ref = :ref", nativeQuery = true)
	int release(String type, String ref);

	boolean existsByTypeAndRef(String type, String ref);
}
