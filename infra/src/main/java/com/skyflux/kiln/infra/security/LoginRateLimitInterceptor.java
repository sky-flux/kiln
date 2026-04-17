package com.skyflux.kiln.infra.security;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-client-IP sliding-window rate-limit on {@code POST /api/v1/auth/login}.
 *
 * <p>Maintains a {@code ConcurrentHashMap<String, Deque<Instant>>} of attempt
 * timestamps keyed by {@link HttpServletRequest#getRemoteAddr()}. On each
 * qualifying request the per-IP deque is locked while expired entries
 * (&lt; {@code now - window}) are pruned and the current size is checked
 * against {@code maxAttempts}. Exceeding the cap causes an
 * {@link AppException} with {@link AppCode#RATE_LIMITED} — the global
 * exception handler translates that into a 429 JSON response, giving us one
 * source of truth for the error envelope.
 *
 * <h3>Scope</h3>
 * Runs at the Spring MVC interceptor layer (after {@code MdcFilter}, so the
 * rejected response still carries a {@code traceId}). Only activates when the
 * request method is {@code POST} AND the URI matches {@code /api/v1/auth/login}
 * exactly — any other request short-circuits to pass-through, keeping the
 * filter chain lean for the common case.
 *
 * <h3>Thread-safety</h3>
 * The outer map is concurrent. The prune-check-add sequence on a single IP is
 * serialised by synchronising on the per-IP deque; lock granularity is at the
 * IP level so concurrent attempts from different clients never contend. The
 * {@code computeIfAbsent} lambda is guaranteed to run at most once per key by
 * {@code ConcurrentHashMap}.
 *
 * <h3>Client IP resolution</h3>
 * {@code request.getRemoteAddr()} only — {@code X-Forwarded-For} parsing is
 * deferred to Phase 5 (requires a trusted-proxy allowlist; naively honouring
 * the header lets any attacker spoof per-IP quotas).
 *
 * <h3>Memory bound</h3>
 * Map grows with unique attacker IPs within the active window. Acceptable for
 * Phase 4.3: process restart clears state, and per-IP deques are bounded to
 * {@code maxAttempts} size. Eviction of fully-expired buckets is a Phase 5+
 * nice-to-have.
 */
public class LoginRateLimitInterceptor implements HandlerInterceptor {

    private static final String LOGIN_PATH = "/api/v1/auth/login";

    private final SecurityProperties properties;
    private final Clock clock;
    private final Map<String, Deque<Instant>> buckets = new ConcurrentHashMap<>();

    public LoginRateLimitInterceptor(SecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        SecurityProperties.RateLimit cfg = properties.rateLimit();
        if (cfg == null || !cfg.enabled()) {
            return true;
        }
        if (!isTargeted(request)) {
            return true;
        }

        String clientIp = resolveClientIp(request);
        int max = cfg.maxAttempts();
        Duration window = cfg.window();
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);

        Deque<Instant> bucket = buckets.computeIfAbsent(clientIp, k -> new ArrayDeque<>());
        synchronized (bucket) {
            // Prune expired (oldest-first).
            while (!bucket.isEmpty() && !bucket.peekFirst().isAfter(cutoff)) {
                bucket.pollFirst();
            }
            if (bucket.size() >= max) {
                throw new AppException(AppCode.RATE_LIMITED);
            }
            bucket.addLast(now);
        }
        // Gate 3 H1: atomic compare-remove so a quiesced IP stops holding a map
        // entry. Still unbounded under concurrent unique-IP bursts, but the
        // steady-state leak from IP rotation is fixed. XFF / LRU cap remain
        // TODO for Phase 5.
        if (bucket.isEmpty()) {
            buckets.remove(clientIp, bucket);
        }
        return true;
    }

    /**
     * Test seam — clears all bucket state. Not part of the public contract.
     * Phase 4.3 Gate 3 H5: lets @SpringBootTest classes reset between tests
     * without restarting the context.
     */
    void clearBucketsForTesting() {
        buckets.clear();
    }

    private static boolean isTargeted(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
                && LOGIN_PATH.equals(request.getRequestURI());
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return (ip == null || ip.isBlank()) ? "unknown" : ip;
    }
}
