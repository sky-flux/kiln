package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.api.AuditService;
import com.skyflux.kiln.audit.domain.AuditAction;
import com.skyflux.kiln.audit.domain.AuditResource;
import com.skyflux.kiln.user.domain.event.UserRegistered;
import com.skyflux.kiln.user.domain.model.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link UserLifecycleAuditListener}. Mocks {@link AuditService};
 * invokes the listener method directly — transaction semantics are provided by
 * Spring annotations and not exercised here.
 */
@ExtendWith(MockitoExtension.class)
class UserLifecycleAuditListenerTest {

    @Mock
    private AuditService auditService;

    @InjectMocks
    private UserLifecycleAuditListener listener;

    @Test
    void userRegisteredRecordsUserCreateWithActorAndTargetAsSameUserId() {
        UUID userId = UUID.randomUUID();
        UserRegistered event = new UserRegistered(
                new UserId(userId), UUID.randomUUID(), "alice@example.com", Instant.parse("2026-04-18T10:00:00Z"));

        listener.on(event);

        verify(auditService).record(
                eq(AuditResource.USER), eq(AuditAction.CREATE),
                eq(userId), eq(userId),
                argThat(details -> details != null && details.contains("email")),
                isNull());
    }
}
