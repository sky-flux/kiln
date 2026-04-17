package com.skyflux.kiln.user.application.port.out;

import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;

import java.util.Optional;

/** Outbound port: persistence abstraction for the {@code User} aggregate. */
public interface UserRepository {
    Optional<User> findById(UserId id);

    void save(User user);
}
