package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.api.AuditService;
import com.skyflux.kiln.audit.domain.AuditAction;
import com.skyflux.kiln.audit.domain.AuditResource;
import com.skyflux.kiln.member.api.MemberEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
class MemberAuditListener {

    private final AuditService auditService;

    MemberAuditListener(AuditService auditService) { this.auditService = auditService; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onPointsAwarded(MemberEvent.PointsAwarded e) {
        auditService.record(AuditResource.MEMBER, AuditAction.AWARD_POINTS,
            e.userId(), null,
            AuditDetailsJson.from(Map.of("points", e.points())), null);
    }
}
