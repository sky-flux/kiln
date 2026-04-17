package com.skyflux.kiln.user.application.port.in;

/** Inbound port: authenticate a user with email + password and start a Sa-Token session. */
public interface AuthenticateUserUseCase {

    /**
     * Verify credentials and start a Sa-Token session.
     *
     * @return the newly issued Sa-Token token value (opaque to the caller)
     * @throws com.skyflux.kiln.common.exception.AppException
     *         with {@code AppCode.LOGIN_FAILED} when the email is unknown
     *         OR the password does not match — both paths surface the same
     *         error code so an attacker cannot enumerate valid emails via
     *         login response codes.
     */
    String execute(Command command);

    /** Command bundle for the login flow. {@code password} plaintext must not leave the use case scope. */
    record Command(String email, String password) {}
}
