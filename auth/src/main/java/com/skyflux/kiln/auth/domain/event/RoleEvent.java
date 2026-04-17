package com.skyflux.kiln.auth.domain.event;

import com.skyflux.kiln.auth.domain.RoleCode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Sealed family of role-lifecycle events, published by the auth module when a
 * {@link RoleCode} is assigned to or revoked from a user.
 *
 * <p>Exposed through the Modulith named interface {@code events} on this
 * package, so downstream modules (e.g. {@code audit}) can listen via
 * {@code @TransactionalEventListener(AFTER_COMMIT)} without importing anything
 * else from {@code auth}.
 *
 * <p>Idempotency note: the underlying DB write uses {@code ON CONFLICT DO
 * NOTHING}, so a redundant {@code assign(...)} of an already-assigned role
 * still publishes a {@code RoleAssigned} event. Listeners must be idempotent
 * (the audit log may show duplicate rows on a no-op assign, which is
 * acceptable).
 */
public sealed interface RoleEvent
        permits RoleEvent.RoleAssigned, RoleEvent.RoleRevoked {

    UUID userId();

    RoleCode role();

    Instant occurredAt();

    /** Emitted after a successful {@code userRoles.assign(...)}. */
    record RoleAssigned(UUID userId, RoleCode role, Instant occurredAt)
            implements RoleEvent {
        public RoleAssigned {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    /** Emitted after a successful {@code userRoles.revoke(...)}. */
    record RoleRevoked(UUID userId, RoleCode role, Instant occurredAt)
            implements RoleEvent {
        public RoleRevoked {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }
}
