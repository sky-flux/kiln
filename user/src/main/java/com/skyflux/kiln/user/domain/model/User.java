package com.skyflux.kiln.user.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * User aggregate root. Framework-free: no Spring, no JPA, no Jackson annotations.
 *
 * <p>Construction is only allowed via the factory methods:
 * <ul>
 *   <li>{@link #register(String, String, String)} — new users</li>
 *   <li>{@link #reconstitute(UserId, String, String, String, int, Instant)} — rebuilding from persistence</li>
 * </ul>
 *
 * <p>The aggregate stores the <em>already-hashed</em> password string (produced by
 * {@code common.security.PasswordService}). Plaintext passwords never enter the
 * domain — hashing happens in the application layer before {@code register(...)}.
 *
 * <p>Phase 4.3 Wave 1 adds two passive lockout-bookkeeping carriers:
 * {@code failedLoginAttempts} and {@code lockedUntil}. Wave 1 is schema +
 * accessors only — the fields ride through persistence but no behavior uses
 * them yet. Wave 2 introduces {@code isLocked()}, {@code registerLoginFailure()},
 * {@code registerLoginSuccess()}, and the threshold checks that mutate them.
 */
public final class User {
    private final UserId id;
    private final String name;
    private final String email;
    private final String passwordHash;
    private final int failedLoginAttempts;
    private final Instant lockedUntil;

    private User(UserId id,
                 String name,
                 String email,
                 String passwordHash,
                 int failedLoginAttempts,
                 Instant lockedUntil) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
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
     *   <li>{@code name}: trimmed</li>
     *   <li>{@code email}: trimmed + lowercased (using {@link java.util.Locale#ROOT}
     *       so the result is stable regardless of host locale)</li>
     *   <li>{@code passwordHash}: stored verbatim (a hash contains significant
     *       characters — leading {@code $}, mixed case — that must not be normalized)</li>
     * </ul>
     *
     * <p>Lockout bookkeeping starts at {@code 0 / null} — the user has never
     * failed a login because they have not yet had a chance to.
     */
    public static User register(String name, String email, String passwordHash) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(email, "email");
        String normalizedName = name.trim();
        String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
        return new User(UserId.newId(), normalizedName, normalizedEmail, passwordHash, 0, null);
    }

    /**
     * Factory for rebuilding an already-persisted user. Does NOT normalize —
     * the DB state is authoritative and must be preserved byte-for-byte.
     */
    public static User reconstitute(UserId id,
                                    String name,
                                    String email,
                                    String passwordHash,
                                    int failedLoginAttempts,
                                    Instant lockedUntil) {
        return new User(id, name, email, passwordHash, failedLoginAttempts, lockedUntil);
    }

    public UserId id() {
        return id;
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
            return new User(id, name, email, passwordHash, 0, now.plus(lockDuration));
        }
        return new User(id, name, email, passwordHash, next, lockedUntil);
    }

    /** Clear counter + any lock (lock is semantically "for this login window"). */
    public User registerLoginSuccess() {
        return new User(id, name, email, passwordHash, 0, null);
    }
}
