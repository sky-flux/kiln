package com.skyflux.kiln.auth.internal;

import com.skyflux.kiln.auth.api.RoleAssignmentService;
import com.skyflux.kiln.auth.domain.RoleCode;
import com.skyflux.kiln.user.domain.event.UserRegistered;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Auto-assigns the default {@link RoleCode#USER} role after a
 * {@code UserRegistered} event commits.
 *
 * <p>Deliberately NOT using {@code @ApplicationModuleListener}: that
 * meta-annotation also applies {@code @Async}, which is a no-op here because
 * the application does not activate {@code @EnableAsync}. Relying on the silent
 * sync fallback would make the contract flip the moment async is turned on
 * elsewhere. Instead the annotations are spelled out so the contract is:
 * synchronous invocation on the caller's thread, in a fresh transaction, after
 * the outer registration transaction commits. If that commit then fails for
 * any reason (connection drop, constraint violation), the exception surfaces
 * to the caller: the committed user is NOT unwound, but the caller sees a 5xx
 * and can retry — no silent role loss.
 *
 * <p>Switching to async + retry requires (1) enabling {@code @EnableAsync},
 * (2) adding {@code spring-modulith-events-jdbc} for the Event Publication
 * Registry, and (3) swapping back to {@code @ApplicationModuleListener}. The
 * current classpath has none of those, so keep this shape.
 */
@Component
class UserRegisteredListener {

    private final RoleAssignmentService roles;

    UserRegisteredListener(RoleAssignmentService roles) {
        this.roles = roles;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(UserRegistered event) {
        roles.assign(event.userId().value(), RoleCode.USER);
    }
}
