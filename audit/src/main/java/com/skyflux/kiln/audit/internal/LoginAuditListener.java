package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.api.AuditService;
import com.skyflux.kiln.audit.domain.AuditType;
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
 * <p>The {@code reason} tag on {@code LoginFailed} is sourced from the
 * application's own enum-like whitelist ({@code UNKNOWN_EMAIL},
 * {@code WRONG_PASSWORD}, {@code ACCOUNT_LOCKED}), never user input — so the
 * hand-built JSON string is safe from injection. The event carries its own
 * {@code requestId} (populated at publish time from MDC), so listeners do not
 * re-read MDC from their own thread.
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
        auditService.record(
                AuditType.LOGIN_SUCCESS, userId, userId, null, event.requestId());
    }

    // REQUIRES_NEW mandatory per RestrictedTransactionalEventListenerFactory — see UserLifecycleAuditListener.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(LoginEvent.LoginFailed event) {
        // Gate 3 C3: the caller is unauthenticated at this point (pre-auth),
        // so actor_user_id is always null. target_user_id names the account
        // under attack (null if the email didn't resolve).
        UUID target = event.targetUserId();
        String details = AuditDetailsJson.from(Map.of("reason", event.reason()));
        auditService.record(
                AuditType.LOGIN_FAILED, null, target, details, event.requestId());
    }
}
