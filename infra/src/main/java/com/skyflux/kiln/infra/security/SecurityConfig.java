package com.skyflux.kiln.infra.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

/**
 * Spring wiring for the security sub-package.
 *
 * <p>Registers:
 * <ul>
 *   <li>{@link SecurityProperties} — binds the YAML stanza
 *       {@code kiln.security.login}.</li>
 *   <li>A system {@link Clock} (UTC) for deterministic, injectable time —
 *       overridable in tests. Only created if no {@code Clock} bean exists
 *       (other config may already provide one).</li>
 *   <li>{@link LoginRateLimitInterceptor} — per-IP sliding-window throttle on
 *       {@code POST /api/v1/auth/login}. Gated by
 *       {@code kiln.security.login.rate-limit.enabled} (default {@code true}).</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnProperty(
            name = "kiln.security.login.rate-limit.enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public LoginRateLimitInterceptor loginRateLimitInterceptor(SecurityProperties props, Clock clock) {
        return new LoginRateLimitInterceptor(props, clock);
    }

    /**
     * MVC glue that mounts {@link LoginRateLimitInterceptor} into the handler
     * chain. Declared as a bean (not a separate {@code @Configuration}) so it
     * is only created when the interceptor bean itself exists — i.e. when
     * rate-limiting is enabled.
     */
    @Bean
    @ConditionalOnProperty(
            name = "kiln.security.login.rate-limit.enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public WebMvcConfigurer loginRateLimitWebMvcConfigurer(LoginRateLimitInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor).addPathPatterns("/api/v1/auth/login");
            }
        };
    }
}
