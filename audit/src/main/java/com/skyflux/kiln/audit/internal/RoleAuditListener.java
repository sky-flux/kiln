package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.api.AuditService;
import com.skyflux.kiln.audit.domain.AuditAction;
import com.skyflux.kiln.audit.domain.AuditResource;
import com.skyflux.kiln.auth.domain.event.RoleEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Audit listener for role-lifecycle events from {@code auth :: events}.
 *
 * <p>See {@link UserLifecycleAuditListener} for the rationale behind the
 * explicit transactional-listener spelling.
 *
 * <p>ROLE_ASSIGNED → resource=ROLE, action=ASSIGN
 * <p>ROLE_REVOKED  → resource=ROLE, action=REVOKE
 */
@Component
class RoleAuditListener {

    private final AuditService auditService;

    RoleAuditListener(AuditService auditService) {
        this.auditService = auditService;
    }

    // REQUIRES_NEW mandatory per RestrictedTransactionalEventListenerFactory — see UserLifecycleAuditListener.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(RoleEvent.RoleAssigned event) {
        String details = AuditDetailsJson.from(java.util.Map.of("role", event.role().value()));
        auditService.record(
                AuditResource.ROLE, AuditAction.ASSIGN, null, event.userId(), details, null);
    }

    // REQUIRES_NEW mandatory per RestrictedTransactionalEventListenerFactory — see UserLifecycleAuditListener.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(RoleEvent.RoleRevoked event) {
        String details = AuditDetailsJson.from(java.util.Map.of("role", event.role().value()));
        auditService.record(
                AuditResource.ROLE, AuditAction.REVOKE, null, event.userId(), details, null);
    }
}
