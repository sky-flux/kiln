package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.domain.Audit;
import com.skyflux.kiln.audit.domain.AuditType;
import com.skyflux.kiln.audit.repo.AuditRepository;
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
    private final AuditRepository repo = mock(AuditRepository.class);
    private final AuditServiceImpl service = new AuditServiceImpl(clock, repo);

    @Test
    void recordStampsOccurredAtFromClockAndReturnsEvent() {
        Audit e = service.record(
                AuditType.LOGIN_SUCCESS, ACTOR, TARGET, "{\"k\":1}", "req-xyz");

        assertThat(e).isNotNull();
        assertThat(e.occurredAt()).isEqualTo(FIXED);
        assertThat(e.type()).isEqualTo(AuditType.LOGIN_SUCCESS);
        assertThat(e.actorUserId()).isEqualTo(ACTOR);
        assertThat(e.targetUserId()).isEqualTo(TARGET);
        assertThat(e.details()).isEqualTo("{\"k\":1}");
        assertThat(e.requestId()).isEqualTo("req-xyz");
        assertThat(e.id()).isNotNull();
    }

    @Test
    void recordDelegatesToRepoWithSameEvent() {
        Audit returned = service.record(
                AuditType.USER_REGISTERED, ACTOR, null, null, null);

        ArgumentCaptor<Audit> captor = ArgumentCaptor.forClass(Audit.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue()).isEqualTo(returned);
    }

    @Test
    void recordAllowsNullActorTargetDetailsRequestId() {
        Audit e = service.record(
                AuditType.LOGIN_FAILED, null, null, null, null);

        assertThat(e.actorUserId()).isNull();
        assertThat(e.targetUserId()).isNull();
        assertThat(e.details()).isNull();
        assertThat(e.requestId()).isNull();
        verify(repo).save(e);
    }

    @Test
    void recordGeneratesDistinctIdsAcrossCalls() {
        Audit a = service.record(AuditType.LOGIN_SUCCESS, ACTOR, null, null, null);
        Audit b = service.record(AuditType.LOGIN_SUCCESS, ACTOR, null, null, null);

        assertThat(a.id()).isNotEqualTo(b.id());
    }
}
