package com.skyflux.kiln.user.application.port.in;

import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;

/** Inbound port: update mutable user fields (currently name only). */
public interface UpdateUserUseCase {
    record Command(UserId userId, String name) {}

    User execute(Command cmd);
}
