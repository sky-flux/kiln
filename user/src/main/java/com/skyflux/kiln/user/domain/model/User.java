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

    public static User register(String name, String email) {
        return new User(UserId.newId(), name, email);
    }

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
