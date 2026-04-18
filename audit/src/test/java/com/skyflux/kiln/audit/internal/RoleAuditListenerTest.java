package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.api.AuditService;
import com.skyflux.kiln.audit.domain.AuditAction;
import com.skyflux.kiln.audit.domain.AuditResource;
import com.skyflux.kiln.auth.domain.RoleCode;
import com.skyflux.kiln.auth.domain.event.RoleEvent;
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
 * Unit tests for {@link RoleAuditListener}.
 */
@ExtendWith(MockitoExtension.class)
class RoleAuditListenerTest {

    @Mock
    private AuditService auditService;

    @InjectMocks
    private RoleAuditListener listener;

    private static final Instant NOW = Instant.parse("2026-04-18T10:00:00Z");

    @Test
    void roleAssignedRecordsRoleAssignWithRoleDetail() {
        UUID userId = UUID.randomUUID();
        RoleEvent.RoleAssigned event = new RoleEvent.RoleAssigned(userId, RoleCode.USER, NOW);

        listener.on(event);

        verify(auditService).record(
                eq(AuditResource.ROLE), eq(AuditAction.ASSIGN),
                isNull(), eq(userId),
                argThat(d -> d != null && d.contains("USER")),
                isNull());
    }

    @Test
    void roleRevokedRecordsRoleRevokeWithRoleDetail() {
        UUID userId = UUID.randomUUID();
        RoleEvent.RoleRevoked event = new RoleEvent.RoleRevoked(userId, RoleCode.ADMIN, NOW);

        listener.on(event);

        verify(auditService).record(
                eq(AuditResource.ROLE), eq(AuditAction.REVOKE),
                isNull(), eq(userId),
                argThat(d -> d != null && d.contains("ADMIN")),
                isNull());
    }
}
