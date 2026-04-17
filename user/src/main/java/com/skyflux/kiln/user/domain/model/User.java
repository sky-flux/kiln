package com.skyflux.kiln.user.domain.model;

import java.util.Objects;

/**
 * User aggregate root. Framework-free: no Spring, no JPA, no Jackson annotations.
 *
 * <p>Construction is only allowed via the factory methods:
 * <ul>
 *   <li>{@link #register(String, String, String)} — new users</li>
 *   <li>{@link #reconstitute(UserId, String, String, String)} — rebuilding from persistence</li>
 * </ul>
 *
 * <p>The aggregate stores the <em>already-hashed</em> password string (produced by
 * {@code common.security.PasswordService}). Plaintext passwords never enter the
 * domain — hashing happens in the application layer before {@code register(...)}.
 */
public final class User {
    private final UserId id;
    private final String name;
    private final String email;
    private final String passwordHash;

    private User(UserId id, String name, String email, String passwordHash) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
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
     */
    public static User register(String name, String email, String passwordHash) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(email, "email");
        String normalizedName = name.trim();
        String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
        return new User(UserId.newId(), normalizedName, normalizedEmail, passwordHash);
    }

    /**
     * Factory for rebuilding an already-persisted user. Does NOT normalize —
     * the DB state is authoritative and must be preserved byte-for-byte.
     */
    public static User reconstitute(UserId id, String name, String email, String passwordHash) {
        return new User(id, name, email, passwordHash);
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
}
