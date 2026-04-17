package com.skyflux.kiln.infra.security;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimitInterceptorTest {

    private static final String LOGIN_PATH = "/api/v1/auth/login";

    private static MutableClock clockAt(Instant instant) {
        return new MutableClock(instant);
    }

    private static LoginRateLimitInterceptor interceptor(int max, Duration window, boolean enabled, Clock clock) {
        SecurityProperties props = new SecurityProperties(
                5,
                Duration.ofMinutes(15),
                new SecurityProperties.RateLimit(enabled, max, window)
        );
        return new LoginRateLimitInterceptor(props, clock);
    }

    private static HttpServletRequest loginReq(String ip) {
        return req("POST", LOGIN_PATH, ip);
    }

    private static HttpServletRequest req(String method, String path, String ip) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, path);
        r.setRequestURI(path);
        r.setRemoteAddr(ip);
        return r;
    }

    private static HttpServletResponse resp() {
        return new MockHttpServletResponse();
    }

    @Test
    void allowsUpToMaxAttemptsThenBlocks() throws Exception {
        MutableClock clock = clockAt(Instant.parse("2026-04-18T00:00:00Z"));
        LoginRateLimitInterceptor i = interceptor(3, Duration.ofSeconds(60), true, clock);

        assertThat(i.preHandle(loginReq("1.2.3.4"), resp(), new Object())).isTrue();
        assertThat(i.preHandle(loginReq("1.2.3.4"), resp(), new Object())).isTrue();
        assertThat(i.preHandle(loginReq("1.2.3.4"), resp(), new Object())).isTrue();

        assertThatThrownBy(() -> i.preHandle(loginReq("1.2.3.4"), resp(), new Object()))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.appCode()).isEqualTo(AppCode.RATE_LIMITED));
    }

    @Test
    void slidesWindow() throws Exception {
        MutableClock clock = clockAt(Instant.parse("2026-04-18T00:00:00Z"));
        LoginRateLimitInterceptor i = interceptor(2, Duration.ofSeconds(60), true, clock);

        assertThat(i.preHandle(loginReq("1.1.1.1"), resp(), new Object())).isTrue();
        assertThat(i.preHandle(loginReq("1.1.1.1"), resp(), new Object())).isTrue();
        assertThatThrownBy(() -> i.preHandle(loginReq("1.1.1.1"), resp(), new Object()))
                .isInstanceOf(AppException.class);

        // Advance beyond window — expired timestamps should be pruned.
        clock.advance(Duration.ofSeconds(61));

        assertThat(i.preHandle(loginReq("1.1.1.1"), resp(), new Object())).isTrue();
    }

    @Test
    void perIpSegregation() throws Exception {
        MutableClock clock = clockAt(Instant.parse("2026-04-18T00:00:00Z"));
        LoginRateLimitInterceptor i = interceptor(2, Duration.ofSeconds(60), true, clock);

        assertThat(i.preHandle(loginReq("10.0.0.1"), resp(), new Object())).isTrue();
        assertThat(i.preHandle(loginReq("10.0.0.1"), resp(), new Object())).isTrue();
        assertThatThrownBy(() -> i.preHandle(loginReq("10.0.0.1"), resp(), new Object()))
                .isInstanceOf(AppException.class);

        // IP B has full quota.
        assertThat(i.preHandle(loginReq("10.0.0.2"), resp(), new Object())).isTrue();
        assertThat(i.preHandle(loginReq("10.0.0.2"), resp(), new Object())).isTrue();
    }

    @Test
    void passesThroughWhenNotLoginPath() throws Exception {
        MutableClock clock = clockAt(Instant.parse("2026-04-18T00:00:00Z"));
        LoginRateLimitInterceptor i = interceptor(1, Duration.ofSeconds(60), true, clock);

        for (int k = 0; k < 50; k++) {
            assertThat(i.preHandle(req("GET", "/api/v1/users/42", "1.2.3.4"), resp(), new Object())).isTrue();
        }
    }

    @Test
    void passesThroughWhenMethodIsNotPost() throws Exception {
        MutableClock clock = clockAt(Instant.parse("2026-04-18T00:00:00Z"));
        LoginRateLimitInterceptor i = interceptor(1, Duration.ofSeconds(60), true, clock);

        for (int k = 0; k < 10; k++) {
            assertThat(i.preHandle(req("GET", LOGIN_PATH, "1.2.3.4"), resp(), new Object())).isTrue();
        }
    }

    @Test
    void passesThroughWhenDisabled() throws Exception {
        MutableClock clock = clockAt(Instant.parse("2026-04-18T00:00:00Z"));
        LoginRateLimitInterceptor i = interceptor(1, Duration.ofSeconds(60), false, clock);

        for (int k = 0; k < 10; k++) {
            assertThat(i.preHandle(loginReq("1.2.3.4"), resp(), new Object())).isTrue();
        }
    }

    @Test
    void threadSafeBurstSameIp() throws Exception {
        MutableClock clock = clockAt(Instant.parse("2026-04-18T00:00:00Z"));
        int max = 20;
        LoginRateLimitInterceptor i = interceptor(max, Duration.ofMinutes(5), true, clock);

        int total = 200;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger blocked = new AtomicInteger();

        for (int k = 0; k < total; k++) {
            pool.submit(() -> {
                try {
                    boolean ok = i.preHandle(loginReq("9.9.9.9"), resp(), new Object());
                    if (ok) allowed.incrementAndGet();
                } catch (AppException ex) {
                    blocked.incrementAndGet();
                } catch (Exception ignored) {
                    // should not happen
                }
            });
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(allowed.get()).isEqualTo(max);
        assertThat(blocked.get()).isEqualTo(total - max);
    }

    /** Mutable clock for deterministic window-sliding tests. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) { this.now = now; }
        void advance(Duration d) { this.now = this.now.plus(d); }
        @Override public Instant instant() { return now; }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }
}
