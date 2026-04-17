package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.api.AuditService;
import com.skyflux.kiln.audit.domain.AuditEventType;
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
 * <p>{@code actor} is {@code null}: the current {@code RoleEvent} shape does
 * not carry the admin who performed the assign/revoke. Recording it honestly
 * as {@code null} today is preferable to guessing from MDC. When Phase 5
 * extends the event with the acting admin id, wire it through here.
 * {@code requestId} is {@code null} for the same reason — {@code RoleEvent}
 * does not carry it, and reading MDC from the listener thread is unsafe.
 *
 * <p>The {@code RoleCode} enum value drives the JSON payload, so it is safe
 * to inline via string concatenation.
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
                AuditEventType.ROLE_ASSIGNED, null, event.userId(), details, null);
    }

    // REQUIRES_NEW mandatory per RestrictedTransactionalEventListenerFactory — see UserLifecycleAuditListener.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(RoleEvent.RoleRevoked event) {
        String details = AuditDetailsJson.from(java.util.Map.of("role", event.role().value()));
        auditService.record(
                AuditEventType.ROLE_REVOKED, null, event.userId(), details, null);
    }
}
