package com.skyflux.kiln.user.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link UserId}.
 *
 * <p>Phase 4.1 L5: Before this test existed, {@code UserId.of(String)} was only
 * exercised indirectly by {@code User.register(...)} (which calls
 * {@code UserId.newId()}) and by the web layer (through controller URL parsing).
 * PIT flagged {@code UserId.of} as {@code NO_COVERAGE} — no unit test drove
 * that code path directly. These tests close the loop so mutation analysis can
 * confirm the factory's null- and format-handling branches are asserted.
 */
class UserIdTest {

    @Test
    void newIdGeneratesUniqueInstances() {
        UserId a = UserId.newId();
        UserId b = UserId.newId();
        assertThat(a).isNotEqualTo(b);
        assertThat(a.value()).isNotNull();
        assertThat(b.value()).isNotNull();
    }

    @Test
    void ofParsesValidUuidString() {
        UUID expected = UUID.randomUUID();
        UserId id = UserId.of(expected.toString());
        assertThat(id.value()).isEqualTo(expected);
    }

    @Test
    void ofRejectsMalformedUuid() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> UserId.of("not-a-uuid"));
    }

    @Test
    void ofRejectsEmptyString() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> UserId.of(""));
    }

    @Test
    void ofRejectsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> UserId.of(null));
    }

    @Test
    void constructorRejectsNullValue() {
        assertThatNullPointerException()
                .isThrownBy(() -> new UserId(null))
                .withMessageContaining("value");
    }

    @Test
    void recordEqualityByValue() {
        UUID uuid = UUID.randomUUID();
        UserId a = new UserId(uuid);
        UserId b = new UserId(uuid);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    void ofAndValueRoundTripViaToString() {
        UUID original = UUID.randomUUID();
        UserId parsed = UserId.of(original.toString());
        assertThat(parsed.value()).isEqualTo(original);
        assertThat(parsed.value().toString()).isEqualTo(original.toString());
    }
}
