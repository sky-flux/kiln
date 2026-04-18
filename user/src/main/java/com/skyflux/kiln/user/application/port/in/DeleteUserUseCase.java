package com.skyflux.kiln.user.application.port.in;

import com.skyflux.kiln.user.domain.model.UserId;

/** Inbound port: soft-delete a user by setting status to INACTIVE. */
public interface DeleteUserUseCase {
    void execute(UserId userId);
}
