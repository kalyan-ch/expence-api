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
import java.util.UUID;

/** One notification that went out: {@code type} is what was sent, {@code ref} what it was for (e.g. the day). */
@Entity
@Table(name = "notification_log")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NotificationLog {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	private String type;

	private String ref;

	/** Maintained by Postgres (column DEFAULT); Hibernate only reads it back. */
	@Column(name = "sent_at", insertable = false, updatable = false)
	private Instant sentAt;
}
