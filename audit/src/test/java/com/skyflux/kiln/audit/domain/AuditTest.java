package com.skyflux.kiln.audit.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Record invariants and static-factory behaviour for {@link Audit}.
 *
 * <p>Pure value-object tests — no Spring, no DB. Exercises:
 * <ul>
 *   <li>Compact ctor rejects null {@code id}, {@code occurredAt}, {@code resource}, {@code action}.</li>
 *   <li>Nullable fields ({@code actorUserId}, {@code targetUserId}, {@code details},
 *       {@code requestId}) are actually allowed to be null so that pre-auth /
 *       system-wide events can be logged without synthesising placeholder IDs.</li>
 *   <li>{@code create(Clock, …)} generates a random {@code id} and stamps
 *       {@code occurredAt} from the supplied clock — the clock parameter is
 *       load-bearing for deterministic tests downstream.</li>
 * </ul>
 */
class AuditTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTOR = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TARGET = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant FIXED = Instant.parse("2026-04-18T10:15:30Z");

    @Test
    void shouldRequireResourceAndAction() {
        assertThatNullPointerException()
            .isThrownBy(() -> new Audit(UUID.randomUUID(), Instant.now(),
                null, AuditAction.CREATE, null, null, null, null));
        assertThatNullPointerException()
            .isThrownBy(() -> new Audit(UUID.randomUUID(), Instant.now(),
                AuditResource.USER, null, null, null, null, null));
    }

    @Test
    void createFactoryShouldUseClockAndGenerateId() {
        Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        Audit a = Audit.create(fixed, AuditResource.USER, AuditAction.LOGIN, null, null, null, null);
        assertThat(a.occurredAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(a.resource()).isEqualTo(AuditResource.USER);
        assertThat(a.action()).isEqualTo(AuditAction.LOGIN);
    }

    @Test
    void createGeneratesUuidAndUsesClock() {
        Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);

        Audit e = Audit.create(
                clock, AuditResource.USER, AuditAction.LOGIN, ACTOR, TARGET, "{\"ip\":\"1.2.3.4\"}", "req-1");

        assertThat(e.id()).isNotNull();
        assertThat(e.occurredAt()).isEqualTo(FIXED);
        assertThat(e.resource()).isEqualTo(AuditResource.USER);
        assertThat(e.action()).isEqualTo(AuditAction.LOGIN);
        assertThat(e.actorUserId()).isEqualTo(ACTOR);
        assertThat(e.targetUserId()).isEqualTo(TARGET);
        assertThat(e.details()).isEqualTo("{\"ip\":\"1.2.3.4\"}");
        assertThat(e.requestId()).isEqualTo("req-1");
    }

    @Test
    void createProducesDistinctIdsAcrossCalls() {
        Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);

        Audit a = Audit.create(clock, AuditResource.USER, AuditAction.CREATE, ACTOR, null, null, null);
        Audit b = Audit.create(clock, AuditResource.USER, AuditAction.CREATE, ACTOR, null, null, null);

        assertThat(a.id()).isNotEqualTo(b.id());
    }

    @Test
    void constructorRejectsNullId() {
        assertThatNullPointerException().isThrownBy(() ->
                new Audit(null, FIXED, AuditResource.USER, AuditAction.LOGIN, ACTOR, TARGET, null, null));
    }

    @Test
    void constructorRejectsNullOccurredAt() {
        assertThatNullPointerException().isThrownBy(() ->
                new Audit(ID, null, AuditResource.USER, AuditAction.LOGIN, ACTOR, TARGET, null, null));
    }

    @Test
    void constructorRejectsNullResource() {
        assertThatNullPointerException().isThrownBy(() ->
                new Audit(ID, FIXED, null, AuditAction.LOGIN, ACTOR, TARGET, null, null));
    }

    @Test
    void constructorRejectsNullAction() {
        assertThatNullPointerException().isThrownBy(() ->
                new Audit(ID, FIXED, AuditResource.USER, null, ACTOR, TARGET, null, null));
    }

    @Test
    void nullActorAndTargetAllowed() {
        Audit e = new Audit(
                ID, FIXED, AuditResource.USER, AuditAction.LOGIN, null, null, null, null);

        assertThat(e.actorUserId()).isNull();
        assertThat(e.targetUserId()).isNull();
    }

    @Test
    void detailsAndRequestIdOptional() {
        Audit e = new Audit(
                ID, FIXED, AuditResource.USER, AuditAction.UPDATE, ACTOR, TARGET, null, null);

        assertThat(e.details()).isNull();
        assertThat(e.requestId()).isNull();
    }
}
