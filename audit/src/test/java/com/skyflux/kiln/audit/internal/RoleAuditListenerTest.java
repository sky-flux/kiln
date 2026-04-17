package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.api.AuditService;
import com.skyflux.kiln.audit.domain.AuditEventType;
import com.skyflux.kiln.auth.domain.RoleCode;
import com.skyflux.kiln.auth.domain.event.RoleEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

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
    void roleAssignedRecordsRoleAssignedWithRoleDetail() {
        UUID userId = UUID.randomUUID();
        RoleEvent.RoleAssigned event = new RoleEvent.RoleAssigned(userId, RoleCode.USER, NOW);

        listener.on(event);

        verify(auditService).record(
                AuditEventType.ROLE_ASSIGNED, null, userId,
                "{\"role\":\"USER\"}", null);
    }

    @Test
    void roleRevokedRecordsRoleRevokedWithRoleDetail() {
        UUID userId = UUID.randomUUID();
        RoleEvent.RoleRevoked event = new RoleEvent.RoleRevoked(userId, RoleCode.ADMIN, NOW);

        listener.on(event);

        verify(auditService).record(
                AuditEventType.ROLE_REVOKED, null, userId,
                "{\"role\":\"ADMIN\"}", null);
    }
}
