package com.skyflux.kiln.infra.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPropertiesTest {

    @Test
    void defaultsKickInWhenRawZeroOrNull() {
        SecurityProperties props = new SecurityProperties(0, null, null);

        assertThat(props.lockThreshold()).isEqualTo(5);
        assertThat(props.lockDuration()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void acceptsExplicitValues() {
        SecurityProperties props = new SecurityProperties(10, Duration.ofMinutes(30), null);

        assertThat(props.lockThreshold()).isEqualTo(10);
        assertThat(props.lockDuration()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void rateLimitDefaultsWhenOuterRecordIsUnset() {
        SecurityProperties props = new SecurityProperties(0, null, null);

        assertThat(props.rateLimit()).isNotNull();
        assertThat(props.rateLimit().enabled()).isTrue();
        assertThat(props.rateLimit().maxAttempts()).isEqualTo(10);
        assertThat(props.rateLimit().window()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void rateLimitNestedDefaultsKickInForZeroAndNull() {
        SecurityProperties.RateLimit rl = new SecurityProperties.RateLimit(true, 0, null);

        assertThat(rl.maxAttempts()).isEqualTo(10);
        assertThat(rl.window()).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void rateLimitAcceptsExplicitOverride() {
        SecurityProperties.RateLimit rl = new SecurityProperties.RateLimit(false, 25, Duration.ofSeconds(30));

        assertThat(rl.enabled()).isFalse();
        assertThat(rl.maxAttempts()).isEqualTo(25);
        assertThat(rl.window()).isEqualTo(Duration.ofSeconds(30));
    }
}
