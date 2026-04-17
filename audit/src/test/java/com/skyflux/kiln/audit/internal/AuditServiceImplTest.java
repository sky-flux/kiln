package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.domain.AuditEvent;
import com.skyflux.kiln.audit.domain.AuditEventType;
import com.skyflux.kiln.audit.repo.AuditEventJooqRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AuditServiceImpl}. Real collaborators for {@link Clock}
 * (fixed), mock for the repository. No Spring context.
 */
class AuditServiceImplTest {

    private static final Instant FIXED = Instant.parse("2026-04-18T12:00:00Z");
    private static final UUID ACTOR = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TARGET = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private final Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    private final AuditEventJooqRepository repo = mock(AuditEventJooqRepository.class);
    private final AuditServiceImpl service = new AuditServiceImpl(clock, repo);

    @Test
    void recordStampsOccurredAtFromClockAndReturnsEvent() {
        AuditEvent e = service.record(
                AuditEventType.LOGIN_SUCCESS, ACTOR, TARGET, "{\"k\":1}", "req-xyz");

        assertThat(e).isNotNull();
        assertThat(e.occurredAt()).isEqualTo(FIXED);
        assertThat(e.type()).isEqualTo(AuditEventType.LOGIN_SUCCESS);
        assertThat(e.actorUserId()).isEqualTo(ACTOR);
        assertThat(e.targetUserId()).isEqualTo(TARGET);
        assertThat(e.details()).isEqualTo("{\"k\":1}");
        assertThat(e.requestId()).isEqualTo("req-xyz");
        assertThat(e.id()).isNotNull();
    }

    @Test
    void recordDelegatesToRepoWithSameEvent() {
        AuditEvent returned = service.record(
                AuditEventType.USER_REGISTERED, ACTOR, null, null, null);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue()).isEqualTo(returned);
    }

    @Test
    void recordAllowsNullActorTargetDetailsRequestId() {
        AuditEvent e = service.record(
                AuditEventType.LOGIN_FAILED, null, null, null, null);

        assertThat(e.actorUserId()).isNull();
        assertThat(e.targetUserId()).isNull();
        assertThat(e.details()).isNull();
        assertThat(e.requestId()).isNull();
        verify(repo).save(e);
    }

    @Test
    void recordGeneratesDistinctIdsAcrossCalls() {
        AuditEvent a = service.record(AuditEventType.LOGIN_SUCCESS, ACTOR, null, null, null);
        AuditEvent b = service.record(AuditEventType.LOGIN_SUCCESS, ACTOR, null, null, null);

        assertThat(a.id()).isNotEqualTo(b.id());
    }
}
