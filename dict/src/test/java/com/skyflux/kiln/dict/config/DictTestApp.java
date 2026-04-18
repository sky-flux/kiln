package com.skyflux.kiln.dict.config;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootApplication(scanBasePackages = "com.skyflux.kiln")
public class DictTestApp {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:18.3-alpine");
    }

    @Bean
    @ServiceConnection(name = "redis")
    RedisContainer redis() {
        return new RedisContainer("redis:8.6.2-alpine");
    }
}
