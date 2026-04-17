package com.skyflux.kiln.user.application.port.out;

import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;

import java.util.Optional;

/** Outbound port: persistence abstraction for the {@code User} aggregate. */
public interface UserRepository {
    Optional<User> findById(UserId id);

    /**
     * Look up a user by their (normalized, lowercase) email address.
     *
     * <p>Used by the login flow. Callers are responsible for normalizing the
     * query key — the adapter does a byte-for-byte match against the DB.
     */
    Optional<User> findByEmail(String email);

    void save(User user);
}
