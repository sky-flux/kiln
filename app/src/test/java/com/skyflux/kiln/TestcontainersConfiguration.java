package com.skyflux.kiln;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared Testcontainers-backed PostgreSQL + Redis for integration tests.
 *
 * <p>{@code @ServiceConnection} on each bean wires Spring Boot's
 * datasource / Redis autoconfig to the container at startup; Flyway
 * then runs {@code V1..VN} against the PG container, and Sa-Token's
 * Redis-backed session store uses the Redis container.
 *
 * <p>Import with {@code @Import(TestcontainersConfiguration.class)} on any
 * {@code @SpringBootTest}. Each importing test class gets its own container
 * set via Spring's context cache.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /**
     * Single source of truth for the PostgreSQL image in integration tests.
     */
    public static final String POSTGRES_IMAGE = "postgres:18.3-alpine";

    /**
     * Single source of truth for the Redis image in integration tests.
     */
    public static final String REDIS_IMAGE = "redis:8.6.2-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(POSTGRES_IMAGE);
    }

    @Bean
    @ServiceConnection(name = "redis")
    RedisContainer redisContainer() {
        return new RedisContainer(REDIS_IMAGE);
    }
}
