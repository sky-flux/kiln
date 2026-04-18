package com.skyflux.kiln.user.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * User aggregate root. Framework-free: no Spring, no JPA, no Jackson annotations.
 *
 * <p>Construction is only allowed via the factory methods:
 * <ul>
 *   <li>{@link #register(UUID, String, String, String)} — new users</li>
 *   <li>{@link #reconstitute(UserId, UUID, String, String, String, int, Instant, String)} — rebuilding from persistence</li>
 * </ul>
 *
 * <p>The aggregate stores the <em>already-hashed</em> password string (produced by
 * {@code common.security.PasswordService}). Plaintext passwords never enter the
 * domain — hashing happens in the application layer before {@code register(...)}.
 *
 * <p>Wave 1 T8 adds {@code tenantId}: every user belongs to exactly one tenant.
 * The tenant is established at registration time via {@link com.skyflux.kiln.tenant.api.TenantContext}
 * and stored verbatim through the persistence round-trip.
 *
 * <p>Phase 4.3 Wave 1 adds two passive lockout-bookkeeping carriers:
 * {@code failedLoginAttempts} and {@code lockedUntil}. Wave 1 is schema +
 * accessors only — the fields ride through persistence but no behavior uses
 * them yet. Wave 2 introduces {@code isLocked()}, {@code registerLoginFailure()},
 * {@code registerLoginSuccess()}, and the threshold checks that mutate them.
 *
 * <p>Wave 2a adds {@code status}: ACTIVE / INACTIVE. New users start ACTIVE.
 * {@link #deactivate()} transitions to INACTIVE (soft-delete). {@link #withName(String)}
 * supports update use-case.
 */
public final class User {
    private final UserId id;
    private final UUID tenantId;
    private final String name;
    private final String email;
    private final String passwordHash;
    private final int failedLoginAttempts;
    private final Instant lockedUntil;
    private final String status;

    private User(UserId id,
                 UUID tenantId,
                 String name,
                 String email,
                 String passwordHash,
                 int failedLoginAttempts,
                 Instant lockedUntil,
                 String status) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.name = Objects.requireNonNull(name, "name");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
        this.status = Objects.requireNonNull(status, "status");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name blank");
        }
        if (!email.contains("@")) {
            throw new IllegalArgumentException("email invalid");
        }
        if (passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash blank");
        }
    }

    /**
     * Factory for newly-registered users. Normalizes inputs so application-level
     * uniqueness on {@code email} is case-insensitive (the DB's {@code UNIQUE}
     * constraint on {@code email} is case-sensitive).
     *
     * <ul>
     *   <li>{@code tenantId}: stored verbatim — identifies the owning tenant</li>
     *   <li>{@code name}: trimmed</li>
     *   <li>{@code email}: trimmed + lowercased (using {@link java.util.Locale#ROOT}
     *       so the result is stable regardless of host locale)</li>
     *   <li>{@code passwordHash}: stored verbatim (a hash contains significant
     *       characters — leading {@code $}, mixed case — that must not be normalized)</li>
     * </ul>
     *
     * <p>Lockout bookkeeping starts at {@code 0 / null} — the user has never
     * failed a login because they have not yet had a chance to.
     *
     * <p>Status defaults to {@code "ACTIVE"}.
     */
    public static User register(UUID tenantId, String name, String email, String passwordHash) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(email, "email");
        String normalizedName = name.trim();
        String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
        return new User(UserId.newId(), tenantId, normalizedName, normalizedEmail, passwordHash, 0, null, "ACTIVE");
    }

    /**
     * Factory for rebuilding an already-persisted user. Does NOT normalize —
     * the DB state is authoritative and must be preserved byte-for-byte.
     */
    public static User reconstitute(UserId id,
                                    UUID tenantId,
                                    String name,
                                    String email,
                                    String passwordHash,
                                    int failedLoginAttempts,
                                    Instant lockedUntil,
                                    String status) {
        return new User(id, tenantId, name, email, passwordHash, failedLoginAttempts, lockedUntil, status);
    }

    public UserId id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public String name() {
        return name;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public int failedLoginAttempts() {
        return failedLoginAttempts;
    }

    /** May be {@code null} — an unlocked user carries no lock-expiry timestamp. */
    public Instant lockedUntil() {
        return lockedUntil;
    }

    /** ACTIVE or INACTIVE. */
    public String status() {
        return status;
    }

    /** True iff a currently-valid lock is in effect. */
    public boolean isLocked(Instant now) {
        Objects.requireNonNull(now, "now");
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /**
     * Record one failed login. If post-increment attempts reaches or exceeds
     * {@code lockThreshold}, lock the account until {@code now + lockDuration}
     * AND reset the counter to 0 (the counter is consumed when the lock trips;
     * after the lock expires the user gets a fresh N attempts).
     * Returns a new User — aggregates are immutable.
     */
    public User registerLoginFailure(Instant now, int lockThreshold, Duration lockDuration) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(lockDuration, "lockDuration");
        if (lockThreshold < 1) {
            throw new IllegalArgumentException("lockThreshold must be \u2265 1");
        }

        int next = failedLoginAttempts + 1;
        if (next >= lockThreshold) {
            return new User(id, tenantId, name, email, passwordHash, 0, now.plus(lockDuration), status);
        }
        return new User(id, tenantId, name, email, passwordHash, next, lockedUntil, status);
    }

    /** Clear counter + any lock (lock is semantically "for this login window"). */
    public User registerLoginSuccess() {
        return new User(id, tenantId, name, email, passwordHash, 0, null, status);
    }

    /**
     * Transition this user to INACTIVE (soft-delete).
     * Throws {@link IllegalStateException} if already INACTIVE.
     */
    public User deactivate() {
        if ("INACTIVE".equals(status)) {
            throw new IllegalStateException("User already inactive");
        }
        return new User(id, tenantId, name, email, passwordHash, failedLoginAttempts, lockedUntil, "INACTIVE");
    }

    /**
     * Return a copy of this user with the name updated. The new name is trimmed.
     *
     * @throws IllegalArgumentException if {@code newName} is blank after trimming
     */
    public User withName(String newName) {
        String n = Objects.requireNonNull(newName, "newName").trim();
        if (n.isBlank()) {
            throw new IllegalArgumentException("name blank");
        }
        return new User(id, tenantId, n, email, passwordHash, failedLoginAttempts, lockedUntil, status);
    }
}
