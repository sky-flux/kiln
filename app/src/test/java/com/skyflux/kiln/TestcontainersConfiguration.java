package com.skyflux.kiln;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared Testcontainers-backed PostgreSQL for integration tests.
 *
 * <p>{@code @ServiceConnection} on the bean wires Spring Boot's datasource
 * autoconfig to the container's jdbc URL / user / password at startup;
 * Flyway then runs {@code V1__init_schema.sql} against it.
 *
 * <p>Import with {@code @Import(TestcontainersConfiguration.class)} on any
 * {@code @SpringBootTest}. Each importing test class gets its own container
 * via Spring's context cache.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Single source of truth for the PostgreSQL image in integration tests. */
    public static final String POSTGRES_IMAGE = "postgres:18-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(POSTGRES_IMAGE);
    }
}
