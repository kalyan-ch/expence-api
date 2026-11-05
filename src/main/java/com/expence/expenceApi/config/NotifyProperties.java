package com.expence.expenceApi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/**
 * Bill reminders. A blank {@code webhookUrl} turns them off. {@code cron} and {@code zone} set when the
 * daily digest goes out; a blank zone means the machine's own. {@code appUrl} is where the messages link.
 */
@ConfigurationProperties("app.notify")
public record NotifyProperties(String webhookUrl, String cron, String zone, int leadDays, String appUrl) {

	public ZoneId zoneId() {
		return zone == null || zone.isBlank() ? ZoneId.systemDefault() : ZoneId.of(zone);
	}
}
