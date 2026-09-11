package com.expence.expenceApi;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for every integration test. Extending it gives the test a real Postgres
 * with the Flyway migrations already applied.
 *
 * <p>The container is a {@code static final} singleton started once in a static
 * initialiser rather than being managed by {@code @Testcontainers}/{@code @Container}.
 * That annotation pair ties the container's lifecycle to the test class, so JUnit would
 * stop it after each class and start a fresh one for the next — paying the Postgres
 * startup cost once per test class instead of once per run. Started this way the JVM
 * keeps it alive for the whole suite and Ryuk reaps it when the JVM exits.
 *
 * <p>{@code @ServiceConnection} hands the container's JDBC URL, username and password
 * to Spring, so no test profile ever has to name a datasource.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

	@ServiceConnection
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.4-alpine");

	static {
		POSTGRES.start();
	}

	/**
	 * Flyway is forced on because the migrations are the schema {@code ddl-auto: validate}
	 * checks the entities against — running the suite without them would validate against
	 * an empty database. It also means a broken migration fails the build.
	 */
	@DynamicPropertySource
	static void flywayProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.flyway.enabled", () -> true);
	}

}
