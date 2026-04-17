package com.skyflux.kiln.user.application.port.in;

import com.skyflux.kiln.user.domain.model.UserId;

/** Inbound port: register a new user. */
public interface RegisterUserUseCase {

    /**
     * Execute the register-user flow. Returns the newly assigned {@link UserId}.
     *
     * @throws com.skyflux.kiln.common.exception.AppException
     *         with {@code AppCode.VALIDATION_FAILED} on blank inputs or a
     *         rejected password, or {@code AppCode.CONFLICT} when the email is
     *         already taken.
     */
    UserId execute(Command command);

    /**
     * Command bundle for registration. {@code password} is the plaintext that
     * the use case hashes before the aggregate is created; the plaintext must
     * never leave the use-case scope.
     */
    record Command(String name, String email, String password) {}
}
