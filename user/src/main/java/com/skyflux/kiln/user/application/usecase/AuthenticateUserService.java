package com.skyflux.kiln.user.application.usecase;

import cn.dev33.satoken.stp.StpUtil;
import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.common.security.PasswordService;
import com.skyflux.kiln.user.application.port.in.AuthenticateUserUseCase;
import com.skyflux.kiln.user.application.port.out.UserRepository;
import com.skyflux.kiln.user.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Use-case implementation for authenticating a user.
 *
 * <p>Both failure modes (unknown email / wrong password) raise
 * {@code AppException(LOGIN_FAILED)} — the response CODE is unified. To
 * also unify response TIMING, the unknown-email branch still runs a
 * "dummy" Argon2id verify against a fixed hash (review finding I1). Without
 * it, an attacker with a stopwatch can distinguish:
 * <ul>
 *   <li>~1 ms: unknown email (no Argon2id work)</li>
 *   <li>~20-40 ms: known email, wrong password (full Argon2id verify)</li>
 * </ul>
 * The dummy verify runs in the same Argon2id memory/CPU envelope, so both
 * paths return in the same time window.
 *
 * <p>Email is normalized with {@code trim + lowercase(ROOT)} before the
 * repository lookup — the DB stores emails lowercased (per
 * {@link User#register}), so the same normalization keeps mixed-case logins
 * working.
 *
 * <p>On success, the Sa-Token session is started via {@link StpUtil#login}
 * keyed by the user id, and the issued token value is returned to the caller.
 */
@Service
@Transactional(readOnly = true)
class AuthenticateUserService implements AuthenticateUserUseCase {

    /**
     * Canary hash used to equalize timing between unknown-email and
     * wrong-password branches. Generated once at class-load using the same
     * Argon2id parameters as real hashes — no plaintext ever matches it.
     */
    private final String canaryHash;

    private final UserRepository repo;
    private final PasswordService passwordService;

    AuthenticateUserService(UserRepository repo, PasswordService passwordService) {
        this.repo = repo;
        this.passwordService = passwordService;
        this.canaryHash = passwordService.hash("canary-" + System.nanoTime());
    }

    @Override
    public String execute(Command cmd) {
        Objects.requireNonNull(cmd, "cmd");

        String normalizedEmail = cmd.email() == null
                ? null
                : cmd.email().trim().toLowerCase(Locale.ROOT);

        Optional<User> found = repo.findByEmail(normalizedEmail);
        if (found.isEmpty()) {
            // I1: consume the same ~20-40 ms Argon2id envelope so timing
            // doesn't leak existence. Result is discarded; canary never matches.
            passwordService.verify(cmd.password(), canaryHash);
            throw new AppException(AppCode.LOGIN_FAILED);
        }
        User user = found.get();

        if (!passwordService.verify(cmd.password(), user.passwordHash())) {
            throw new AppException(AppCode.LOGIN_FAILED);
        }

        StpUtil.login(user.id().value().toString());
        return StpUtil.getTokenValue();
    }
}
