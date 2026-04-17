package com.skyflux.kiln.infra.cors;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Global CORS configuration (Ch.4.10).
 *
 * <p>Uses {@link CorsConfiguration#setAllowedOriginPatterns(List)} (not
 * {@code setAllowedOrigins}) so that wildcard patterns like
 * {@code https://*.example.com} coexist with {@code allowCredentials=true}.
 *
 * <p>Fails fast at bean construction if any configured origin contains an
 * unresolved Spring placeholder (e.g. {@code ${CORS_ALLOWED_ORIGIN}}) — this
 * prevents silent misbinding in prod when an env var is missing.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

    private static final List<String> ALLOWED_METHODS =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        List<String> origins = resolveOrigins(properties);

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(ALLOWED_METHODS);
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** Validate + sanitize configured origins, or fall back to dev defaults. */
    private static List<String> resolveOrigins(CorsProperties properties) {
        List<String> configured = properties.allowedOrigins();
        if (configured == null) {
            return CorsProperties.DEFAULT_ALLOWED_ORIGINS;
        }

        // Fail fast on any unresolved Spring placeholder — indicates a missing
        // env var in prod that would otherwise silently bind as a literal string.
        for (String origin : configured) {
            if (origin != null && origin.contains("${")) {
                throw new IllegalStateException(
                        "kiln.cors.allowed-origins contains unresolved placeholder: '"
                                + origin + "'. Set the referenced env var or remove the entry.");
            }
        }

        List<String> sanitized = configured.stream()
                .filter(s -> s != null && !s.isBlank())
                .toList();

        return sanitized.isEmpty() ? CorsProperties.DEFAULT_ALLOWED_ORIGINS : sanitized;
    }
}
