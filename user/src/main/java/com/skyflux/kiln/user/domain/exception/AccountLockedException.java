package com.skyflux.kiln.user.domain.exception;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;

/**
 * Raised by {@code AuthenticateUserService} when a login attempt targets a
 * user whose {@code lockedUntil} is still in the future. Translates to
 * HTTP 423 {@code Locked} via {@link AppCode#ACCOUNT_LOCKED} — the generic
 * {@code AppException} handler in {@code GlobalExceptionHandler} already
 * maps this, so no extra handler is required.
 *
 * <p>Unlike the unknown-email / wrong-password pair (which intentionally
 * return the same code to prevent email enumeration), the locked state is
 * deliberately surfaced: an attacker who reaches this branch already knows
 * the email exists, and the user needs to be told why further attempts
 * will not succeed.
 */
public class AccountLockedException extends AppException {
    public AccountLockedException() {
        super(AppCode.ACCOUNT_LOCKED);
    }
}
