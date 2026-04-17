package com.skyflux.kiln.user.domain.event;

import com.skyflux.kiln.user.domain.model.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * Sealed family of login lifecycle events.
 *
 * <p>Two members:
 * <ul>
 *   <li>{@link LoginSucceeded} — password verified, Sa-Token session
 *       started. Carries the resolved {@link UserId}.</li>
 *   <li>{@link LoginFailed} — any authentication failure. The caller of
 *       the login endpoint is intentionally unknown (pre-auth), so no
 *       {@code actorUserId} is carried. {@code targetUserId} names the
 *       account under attack — {@code null} when the submitted email
 *       did not resolve. Phase 4.3 Gate 3 C3: this distinction pins the
 *       "who attacked whom" audit signal that conflating actor+target
 *       would lose.</li>
 * </ul>
 *
 * <p>Kept alongside {@link UserRegistered} as a peer; the {@code domain/event}
 * package carries the full user-module event surface.
 */
public sealed interface LoginEvent
        permits LoginEvent.LoginSucceeded, LoginEvent.LoginFailed {

    Instant occurredAt();

    /** May be {@code null} — mirrors the MDC {@code traceId} when available. */
    String requestId();

    /** Emitted when a user authenticates successfully. */
    record LoginSucceeded(UserId userId, Instant occurredAt, String requestId)
            implements LoginEvent {
        public LoginSucceeded {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    /**
     * Emitted for any authentication failure. {@code targetUserId} is
     * {@code null} when the submitted email did not resolve to a user.
     * {@code reason} is one of {@code UNKNOWN_EMAIL}, {@code WRONG_PASSWORD},
     * {@code ACCOUNT_LOCKED}.
     */
    record LoginFailed(java.util.UUID targetUserId,
                       String reason,
                       Instant occurredAt,
                       String requestId)
            implements LoginEvent {
        public LoginFailed {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(occurredAt, "occurredAt");
            if (reason.isBlank()) {
                throw new IllegalArgumentException("reason blank");
            }
        }
    }
}
