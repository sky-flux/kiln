package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.api.AuditService;
import com.skyflux.kiln.audit.domain.AuditAction;
import com.skyflux.kiln.audit.domain.AuditResource;
import com.skyflux.kiln.user.domain.event.UserRegistered;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Audit listener for user-lifecycle events from {@code user :: events}.
 *
 * <p>Deliberately uses plain Spring {@code @TransactionalEventListener} +
 * {@code @Transactional(REQUIRES_NEW)} rather than
 * {@code @ApplicationModuleListener}: the meta-annotation also implies
 * {@code @Async}, which silently degrades to synchronous invocation when
 * {@code @EnableAsync} is absent — making the contract flip the moment any
 * module turns async on. See
 * {@code auth.internal.UserRegisteredListener} for the precedent (Phase 4.2
 * Gate 3 C1). The outer transaction has already committed by the time this
 * runs, and a {@code REQUIRES_NEW} transaction commits the audit row
 * independently so failures here never unwind the business transaction.
 *
 * <p>USER_REGISTERED → resource=USER, action=CREATE
 */
@Component
class UserLifecycleAuditListener {

    private final AuditService auditService;

    UserLifecycleAuditListener(AuditService auditService) {
        this.auditService = auditService;
    }

    // REQUIRES_NEW is MANDATORY, not optional: Spring's
    // RestrictedTransactionalEventListenerFactory rejects plain @Transactional on
    // @TransactionalEventListener methods because at AFTER_COMMIT the parent tx
    // is already being closed — a new tx is required to persist the audit row.
    // (Gate 3 H2 tried to simplify this; the runtime refuses to start.)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(UserRegistered event) {
        java.util.UUID userId = event.userId().value();
        String details = AuditDetailsJson.from(java.util.Map.of("email", event.email()));
        auditService.record(AuditResource.USER, AuditAction.CREATE, userId, userId, details, null);
    }
}
