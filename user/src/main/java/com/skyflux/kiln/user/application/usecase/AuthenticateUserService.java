package com.skyflux.kiln.user.application.usecase;

import cn.dev33.satoken.stp.StpUtil;
import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.common.security.PasswordService;
import com.skyflux.kiln.infra.security.SecurityProperties;
import com.skyflux.kiln.user.application.port.in.AuthenticateUserUseCase;
import com.skyflux.kiln.user.application.port.out.UserRepository;
import com.skyflux.kiln.user.domain.event.LoginEvent;
import com.skyflux.kiln.user.domain.exception.AccountLockedException;
import com.skyflux.kiln.user.domain.model.User;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Use-case implementation for authenticating a user.
 *
 * <p>Pipeline (Wave 3):
 * <ol>
 *   <li>Normalize email ({@code trim + lowercase(ROOT)}), look up user.</li>
 *   <li>If email unknown — run a canary-hash verify to equalize timing,
 *       publish {@code LoginFailed(reason=UNKNOWN_EMAIL, targetUserId=null)},
 *       throw {@code LOGIN_FAILED}.</li>
 *   <li>If user is locked ({@code isLocked(now)}) — publish
 *       {@code LoginFailed(reason=ACCOUNT_LOCKED)} and throw
 *       {@link AccountLockedException} (HTTP 423). Lockout deliberately
 *       reveals state: the attacker already knows the email exists if they
 *       got here, and the user needs actionable feedback.</li>
 *   <li>Verify the password.
 *       <ul>
 *         <li>On failure — call {@code user.registerLoginFailure(now,
 *             threshold, duration)} (may trip the lock), persist, publish
 *             {@code LoginFailed(reason=WRONG_PASSWORD)}, throw
 *             {@code LOGIN_FAILED}.</li>
 *         <li>On success — call {@code user.registerLoginSuccess()} (clears
 *             counter + lock), persist, publish {@code LoginSucceeded},
 *             start Sa-Token session, return token.</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p>Timing invariant (review finding I1) still holds for the unknown-email
 * vs wrong-password pair: both burn through a full Argon2id verify and both
 * throw {@code LOGIN_FAILED}. The locked branch skips the verify but returns
 * a <em>different</em> status code on purpose — see above.
 *
 * <p>{@code requestId} on published events mirrors the SLF4J MDC
 * {@code traceId} (set by {@code MdcFilter} from the inbound
 * {@code X-Request-Id} header); {@code null} outside a servlet context.
 */
@Service
// Gate 3 C2: noRollbackFor is NARROWED to the wrong-password marker subclass.
// The default @Transactional rollback-on-runtime policy would otherwise
// discard the repo.save(...) that increments failedLoginAttempts before the
// throw (making the lockout threshold unreachable in prod — revealed by
// KilnIntegrationTest#accountLocksAfterThresholdFailedAttempts). Every OTHER
// AppException flavour still rolls back as normal, so a future partial side
// effect followed by e.g. AppException(CONFLICT) is not silently persisted.
@Transactional(noRollbackFor = AuthenticateUserService.PersistedLoginFailed.class)
class AuthenticateUserService implements AuthenticateUserUseCase {

    /**
     * Internal marker: a {@code LOGIN_FAILED} that MUST NOT roll back the
     * enclosing transaction because the lockout counter was already persisted
     * via {@code repo.save(...)}. Extends {@link AppException} so the
     * {@code GlobalExceptionHandler} still translates it to 401. Scoped to
     * this class — nothing outside AuthenticateUserService should throw it.
     */
    static final class PersistedLoginFailed extends AppException {
        PersistedLoginFailed() { super(AppCode.LOGIN_FAILED); }
    }

    /**
     * Canary hash used to equalize timing between unknown-email and
     * wrong-password branches. Generated once at class-load using the same
     * Argon2id parameters as real hashes — no plaintext ever matches it.
     */
    private final String canaryHash;

    private final UserRepository repo;
    private final PasswordService passwordService;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SecurityProperties securityProps;

    AuthenticateUserService(UserRepository repo,
                            PasswordService passwordService,
                            ApplicationEventPublisher events,
                            Clock clock,
                            SecurityProperties securityProps) {
        this.repo = repo;
        this.passwordService = passwordService;
        this.events = events;
        this.clock = clock;
        this.securityProps = securityProps;
        this.canaryHash = passwordService.hash("canary-" + System.nanoTime());
    }

    @Override
    public String execute(Command cmd) {
        Objects.requireNonNull(cmd, "cmd");

        String normalizedEmail = cmd.email() == null
                ? null
                : cmd.email().trim().toLowerCase(Locale.ROOT);

        Instant now = clock.instant();
        String requestId = MDC.get("traceId");

        Optional<User> found = repo.findByEmail(normalizedEmail);
        if (found.isEmpty()) {
            // I1: consume the same ~20-40 ms Argon2id envelope so timing
            // doesn't leak existence. Result is discarded; canary never matches.
            passwordService.verify(cmd.password(), canaryHash);
            events.publishEvent(new LoginEvent.LoginFailed(null, "UNKNOWN_EMAIL", now, requestId));
            throw new AppException(AppCode.LOGIN_FAILED);
        }
        User user = found.get();

        if (user.isLocked(now)) {
            events.publishEvent(new LoginEvent.LoginFailed(
                    user.id().value(), "ACCOUNT_LOCKED", now, requestId));
            throw new AccountLockedException();
        }

        if (!passwordService.verify(cmd.password(), user.passwordHash())) {
            // Gate 3 C1: atomic SQL UPDATE via repo.recordLoginFailure removes
            // the lost-update race that used to make concurrent wrong-password
            // requests miss lockouts. User.registerLoginFailure still exists
            // as the authoritative domain rule (exercised by UserTest).
            repo.recordLoginFailure(
                    user.id(), now, securityProps.lockThreshold(), securityProps.lockDuration());
            events.publishEvent(new LoginEvent.LoginFailed(
                    user.id().value(), "WRONG_PASSWORD", now, requestId));
            // PersistedLoginFailed — the only path where the counter save MUST survive.
            throw new PersistedLoginFailed();
        }

        repo.recordLoginSuccess(user.id());
        events.publishEvent(new LoginEvent.LoginSucceeded(user.id(), now, requestId));

        StpUtil.login(user.id().value().toString());
        return StpUtil.getTokenValue();
    }
}
