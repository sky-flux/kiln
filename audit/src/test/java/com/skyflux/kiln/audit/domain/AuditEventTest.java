package com.skyflux.kiln.audit.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Record invariants and static-factory behaviour for {@link AuditEvent}.
 *
 * <p>Pure value-object tests — no Spring, no DB. Exercises:
 * <ul>
 *   <li>Compact ctor rejects null {@code id}, {@code occurredAt}, {@code type}.</li>
 *   <li>Nullable fields ({@code actorUserId}, {@code targetUserId}, {@code details},
 *       {@code requestId}) are actually allowed to be null so that pre-auth /
 *       system-wide events can be logged without synthesising placeholder IDs.</li>
 *   <li>{@code create(Clock, …)} generates a random {@code id} and stamps
 *       {@code occurredAt} from the supplied clock — the clock parameter is
 *       load-bearing for deterministic tests downstream.</li>
 * </ul>
 */
class AuditEventTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TARGET = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant FIXED = Instant.parse("2026-04-18T10:15:30Z");

    @Test
    void createGeneratesUuidAndUsesClock() {
        Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);

        AuditEvent e = AuditEvent.create(
                clock, AuditEventType.LOGIN_SUCCESS, ACTOR, TARGET, "{\"ip\":\"1.2.3.4\"}", "req-1");

        assertThat(e.id()).isNotNull();
        assertThat(e.occurredAt()).isEqualTo(FIXED);
        assertThat(e.type()).isEqualTo(AuditEventType.LOGIN_SUCCESS);
        assertThat(e.actorUserId()).isEqualTo(ACTOR);
        assertThat(e.targetUserId()).isEqualTo(TARGET);
        assertThat(e.details()).isEqualTo("{\"ip\":\"1.2.3.4\"}");
        assertThat(e.requestId()).isEqualTo("req-1");
    }

    @Test
    void createProducesDistinctIdsAcrossCalls() {
        Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);

        AuditEvent a = AuditEvent.create(clock, AuditEventType.USER_REGISTERED, ACTOR, null, null, null);
        AuditEvent b = AuditEvent.create(clock, AuditEventType.USER_REGISTERED, ACTOR, null, null, null);

        assertThat(a.id()).isNotEqualTo(b.id());
    }

    @Test
    void constructorRejectsNullId() {
        assertThatNullPointerException().isThrownBy(() ->
                new AuditEvent(null, FIXED, AuditEventType.LOGIN_SUCCESS, ACTOR, TARGET, null, null));
    }

    @Test
    void constructorRejectsNullOccurredAt() {
        assertThatNullPointerException().isThrownBy(() ->
                new AuditEvent(ID, null, AuditEventType.LOGIN_SUCCESS, ACTOR, TARGET, null, null));
    }

    @Test
    void constructorRejectsNullType() {
        assertThatNullPointerException().isThrownBy(() ->
                new AuditEvent(ID, FIXED, null, ACTOR, TARGET, null, null));
    }

    @Test
    void nullActorAndTargetAllowed() {
        AuditEvent e = new AuditEvent(
                ID, FIXED, AuditEventType.LOGIN_FAILED, null, null, null, null);

        assertThat(e.actorUserId()).isNull();
        assertThat(e.targetUserId()).isNull();
    }

    @Test
    void detailsAndRequestIdOptional() {
        AuditEvent e = new AuditEvent(
                ID, FIXED, AuditEventType.ACCOUNT_LOCKED, ACTOR, TARGET, null, null);

        assertThat(e.details()).isNull();
        assertThat(e.requestId()).isNull();
    }
}
