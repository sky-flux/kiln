package com.skyflux.kiln.user.domain.model;

import java.util.Objects;

/**
 * User aggregate root. Framework-free: no Spring, no JPA, no Jackson annotations.
 *
 * <p>Construction is only allowed via the factory methods:
 * <ul>
 *   <li>{@link #register(String, String)} — new users</li>
 *   <li>{@link #reconstitute(UserId, String, String)} — rebuilding from persistence</li>
 * </ul>
 */
public final class User {
    private final UserId id;
    private final String name;
    private final String email;

    private User(UserId id, String name, String email) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.email = Objects.requireNonNull(email, "email");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name blank");
        }
        if (!email.contains("@")) {
            throw new IllegalArgumentException("email invalid");
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
     * </ul>
     */
    public static User register(String name, String email) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(email, "email");
        String normalizedName = name.trim();
        String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
        return new User(UserId.newId(), normalizedName, normalizedEmail);
    }

    /**
     * Factory for rebuilding an already-persisted user. Does NOT normalize —
     * the DB state is authoritative and must be preserved byte-for-byte.
     */
    public static User reconstitute(UserId id, String name, String email) {
        return new User(id, name, email);
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
}
