package com.skyflux.kiln.infra.cors;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Type-safe properties backing {@link CorsConfig}.
 *
 * <p>Bound to YAML prefix {@code kiln.cors}. When {@code allowedOrigins} is
 * {@code null}, empty, or contains only blank entries, {@link CorsConfig}
 * applies {@link #DEFAULT_ALLOWED_ORIGINS} (local-dev frontends).
 *
 * <p>Any entry containing an unresolved Spring placeholder ({@code ${…}})
 * triggers a fail-fast at bean construction — see {@link CorsConfig}.
 */
@ConfigurationProperties(prefix = "kiln.cors")
public record CorsProperties(List<String> allowedOrigins) {

    /** Default allowed origins when nothing is configured (local dev frontends). */
    public static final List<String> DEFAULT_ALLOWED_ORIGINS =
            List.of("http://localhost:5173", "http://localhost:3000");
}
