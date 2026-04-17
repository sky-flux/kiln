package com.skyflux.kiln.infra.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Type-safe properties for authentication lockout policy and login rate-limiting.
 *
 * <p>Bound to YAML prefix {@code kiln.security.login}. Defaults kick in when
 * the raw binding produces {@code 0} (unset int) or {@code null} (unset
 * duration / nested record) — see the compact constructors. This lets the
 * aggregate reason about a single normalized shape regardless of whether the
 * application configures the stanza explicitly.
 *
 * <p>Wave 2: {@code lockThreshold} / {@code lockDuration} consumed by
 * {@code User.registerLoginFailure} via {@code AuthenticateUserService}.
 *
 * <p>Wave 3 (this wave): {@link RateLimit} consumed by
 * {@link LoginRateLimitInterceptor} — per-IP sliding-window throttle on
 * {@code POST /api/v1/auth/login}.
 */
@ConfigurationProperties(prefix = "kiln.security.login")
public record SecurityProperties(
        @Min(1) int lockThreshold,
        @NotNull Duration lockDuration,
        RateLimit rateLimit
) {
    public SecurityProperties {
        if (lockThreshold < 1) {
            lockThreshold = 5;
        }
        if (lockDuration == null) {
            lockDuration = Duration.ofMinutes(15);
        }
        if (rateLimit == null) {
            rateLimit = RateLimit.defaults();
        }
    }

    /**
     * Sliding-window rate-limit policy for {@code POST /api/v1/auth/login}.
     *
     * <ul>
     *   <li>{@code enabled} — master switch; when {@code false} the interceptor
     *       short-circuits to pass-through.</li>
     *   <li>{@code maxAttempts} — inclusive cap per client IP within {@link #window()}.</li>
     *   <li>{@code window} — sliding window size; entries older than
     *       {@code now - window} are pruned.</li>
     * </ul>
     */
    public record RateLimit(
            boolean enabled,
            @Min(1) int maxAttempts,
            @NotNull Duration window
    ) {
        public RateLimit {
            if (maxAttempts < 1) {
                maxAttempts = 10;
            }
            if (window == null) {
                window = Duration.ofMinutes(1);
            }
        }

        public static RateLimit defaults() {
            return new RateLimit(true, 10, Duration.ofMinutes(1));
        }
    }
}
