package com.skyflux.kiln.common.result;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RTest {

    @Test
    void okWithDataReturnsSuccessEnvelope() {
        R<String> r = R.ok("payload");

        assertThat(r.code()).isZero();
        assertThat(r.message()).isEqualTo("ok");
        assertThat(r.data()).isEqualTo("payload");
        assertThat(r.timestamp()).isNotNull().isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void okWithoutDataReturnsNullPayload() {
        R<Object> r = R.ok();

        assertThat(r.code()).isZero();
        assertThat(r.message()).isEqualTo("ok");
        assertThat(r.data()).isNull();
        assertThat(r.timestamp()).isNotNull();
    }

    @Test
    void failCarriesCustomCodeAndMessage() {
        R<Object> r = R.fail(1001, "custom msg");

        assertThat(r.code()).isEqualTo(1001);
        assertThat(r.message()).isEqualTo("custom msg");
        assertThat(r.data()).isNull();
        assertThat(r.timestamp()).isNotNull();
    }

    @Test
    void recordEqualityIsByValue() {
        Instant ts = Instant.parse("2026-04-17T00:00:00Z");
        R<String> a = new R<>(0, "ok", "x", ts);
        R<String> b = new R<>(0, "ok", "x", ts);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}
