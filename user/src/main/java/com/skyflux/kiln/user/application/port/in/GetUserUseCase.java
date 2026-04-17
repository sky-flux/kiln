package com.skyflux.kiln.user.application.port.in;

import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;

/** Inbound port: retrieve a user by identifier. */
public interface GetUserUseCase {
    User execute(UserId id);
}
