package com.skyflux.kiln.audit.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Module-level Spring wiring for {@code audit}.
 *
 * <p>Provides a UTC {@link Clock} bean that {@code AuditServiceImpl} consumes
 * to stamp {@code occurredAt}. Guarded by {@link ConditionalOnMissingBean} so
 * tests can override with a fixed clock without bean conflicts, and so a
 * future infra-level Clock bean (if introduced) takes precedence.
 */
@Configuration
public class AuditConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock systemUtcClock() {
        return Clock.systemUTC();
    }
}
