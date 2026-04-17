package com.skyflux.kiln.infra.cors;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsConfigTest {

    private final CorsConfig config = new CorsConfig();

    // ──────────── I6: use allowed-origin PATTERNS (compatible with credentials + wildcards) ────────────

    @Test
    void setsAllowedOriginPatternsNotAllowedOrigins() {
        CorsConfigurationSource source = config.corsConfigurationSource(
                new CorsProperties(List.of("https://example.com")));

        CorsConfiguration cors = resolve(source, "/api/anything");
        assertThat(cors).isNotNull();
        // setAllowedOriginPatterns populates allowedOriginPatterns, not allowedOrigins
        assertThat(cors.getAllowedOriginPatterns()).containsExactly("https://example.com");
        assertThat(cors.getAllowedOrigins()).isNull();
    }

    @Test
    void wildcardPatternIsAllowedWithCredentials() {
        // The whole point of setAllowedOriginPatterns: Spring would reject a bare "*"
        // in setAllowedOrigins when allowCredentials=true; patterns accept it.
        CorsConfigurationSource source = config.corsConfigurationSource(
                new CorsProperties(List.of("https://*.example.com")));
        CorsConfiguration cors = resolve(source, "/");
        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOriginPatterns()).containsExactly("https://*.example.com");
    }

    // ──────────── C1: fail fast on unresolved ${...} placeholders ────────────

    @Test
    void unresolvedPlaceholderInOriginFailsFast() {
        CorsProperties bad = new CorsProperties(List.of("${CORS_ALLOWED_ORIGIN}"));
        assertThatThrownBy(() -> config.corsConfigurationSource(bad))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unresolved placeholder");
    }

    @Test
    void mixedValidAndPlaceholderStillFails() {
        CorsProperties bad = new CorsProperties(List.of("https://good.example.com", "${MISSING}"));
        assertThatThrownBy(() -> config.corsConfigurationSource(bad))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blankOriginsAreFilteredOut() {
        // relaxed-binding can produce single empty string for a single-element list with no env resolution
        CorsConfigurationSource source = config.corsConfigurationSource(
                new CorsProperties(List.of("", " ", "https://example.com")));
        CorsConfiguration cors = resolve(source, "/");
        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOriginPatterns()).containsExactly("https://example.com");
    }

    // ──────────── Defaults (dev fallback) ────────────

    @Test
    void defaultsAppliedWhenPropertyIsNull() {
        CorsConfigurationSource source = config.corsConfigurationSource(new CorsProperties(null));
        CorsConfiguration cors = resolve(source, "/api/anything");
        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOriginPatterns()).containsExactly(
                "http://localhost:5173",
                "http://localhost:3000");
    }

    @Test
    void defaultsAppliedWhenAllOriginsAreBlank() {
        CorsConfigurationSource source = config.corsConfigurationSource(
                new CorsProperties(List.of("", "   ")));
        CorsConfiguration cors = resolve(source, "/api/anything");
        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOriginPatterns())
                .isEqualTo(CorsProperties.DEFAULT_ALLOWED_ORIGINS);
    }

    @Test
    void standardMethodsHeadersAndCredentialsAreSet() {
        CorsConfigurationSource source = config.corsConfigurationSource(
                new CorsProperties(List.of("https://example.com")));
        CorsConfiguration cors = resolve(source, "/api/anything");
        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedMethods())
                .containsExactlyInAnyOrder("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(cors.getAllowedHeaders()).containsExactly("*");
        assertThat(cors.getAllowCredentials()).isTrue();
        assertThat(cors.getMaxAge()).isEqualTo(3600L);
    }

    private static CorsConfiguration resolve(CorsConfigurationSource source, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        HttpServletRequest req = request;
        return source.getCorsConfiguration(req);
    }
}
