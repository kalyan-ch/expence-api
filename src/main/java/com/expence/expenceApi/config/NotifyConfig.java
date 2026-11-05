package com.expence.expenceApi.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/** The clock runs in the reminder zone, so "today" matches the day the digest is scheduled for. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(NotifyProperties.class)
public class NotifyConfig {

	@Bean
	public Clock clock(NotifyProperties props) {
		return Clock.system(props.zoneId());
	}
}
