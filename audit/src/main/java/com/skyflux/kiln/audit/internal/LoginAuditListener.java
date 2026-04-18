package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.api.AuditService;
import com.skyflux.kiln.audit.domain.AuditAction;
import com.skyflux.kiln.audit.domain.AuditResource;
import com.skyflux.kiln.user.domain.event.LoginEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.UUID;

/**
 * Audit listener for login-lifecycle events from {@code user :: events}.
 *
 * <p>See {@link UserLifecycleAuditListener} for the rationale behind the
 * explicit {@code @TransactionalEventListener(AFTER_COMMIT)} +
 * {@code @Transactional(REQUIRES_NEW)} spelling.
 *
 * <p>LoginSucceeded → resource=USER, action=LOGIN, details={"result":"SUCCESS"}
 * <p>LoginFailed   → resource=USER, action=LOGIN, details={"result":"FAILED","reason":...}
 *                    This covers all failure reasons including ACCOUNT_LOCKED, UNKNOWN_EMAIL,
 *                    and WRONG_PASSWORD — all treated as action=LOGIN since the authentication
 *                    attempt occurred, regardless of outcome.
 */
@Component
class LoginAuditListener {

    private final AuditService auditService;

    LoginAuditListener(AuditService auditService) {
        this.auditService = auditService;
    }

    // REQUIRES_NEW mandatory per RestrictedTransactionalEventListenerFactory — see UserLifecycleAuditListener.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(LoginEvent.LoginSucceeded event) {
        UUID userId = event.userId().value();
        String details = AuditDetailsJson.from(Map.of("result", "SUCCESS"));
        auditService.record(
                AuditResource.USER, AuditAction.LOGIN, userId, userId, details, event.requestId());
    }

    // REQUIRES_NEW mandatory per RestrictedTransactionalEventListenerFactory — see UserLifecycleAuditListener.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(LoginEvent.LoginFailed event) {
        // Gate 3 C3: the caller is unauthenticated at this point (pre-auth),
        // so actor_user_id is always null. target_user_id names the account
        // under attack (null if the email didn't resolve).
        UUID target = event.targetUserId();
        String details = AuditDetailsJson.from(Map.of("result", "FAILED", "reason", event.reason()));
        auditService.record(
                AuditResource.USER, AuditAction.LOGIN, null, target, details, event.requestId());
    }
}
