package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.api.AuditService;
import com.skyflux.kiln.audit.domain.AuditAction;
import com.skyflux.kiln.audit.domain.AuditResource;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.member.api.MemberEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberAuditListenerTest {

    @Mock AuditService auditService;
    @InjectMocks MemberAuditListener listener;

    @Test void shouldAuditPointsAwarded() {
        listener.onPointsAwarded(new MemberEvent.PointsAwarded(
            Ids.next(), Ids.next(), 150, Instant.now()));
        verify(auditService).record(eq(AuditResource.MEMBER), eq(AuditAction.AWARD_POINTS),
            any(), isNull(), contains("points"), isNull());
    }
}
