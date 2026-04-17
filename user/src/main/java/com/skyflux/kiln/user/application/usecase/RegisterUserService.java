package com.skyflux.kiln.user.application.usecase;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.common.security.PasswordService;
import com.skyflux.kiln.user.application.port.in.RegisterUserUseCase;
import com.skyflux.kiln.user.application.port.out.UserRepository;
import com.skyflux.kiln.user.domain.event.UserRegistered;
import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Use-case implementation for registering a new user.
 *
 * <p>Transaction boundary lives here (design.md Ch 5.7). The plaintext
 * password is hashed by {@link PasswordService} (Argon2id impl lives in
 * {@code infra/security/}) and only the hash reaches the aggregate — the
 * plaintext never enters the domain or persistence layers.
 *
 * <p>Error translation:
 * <ul>
 *   <li>{@link NullPointerException} / {@link IllegalArgumentException} from
 *       the password hashing or aggregate invariant checks → {@code VALIDATION_FAILED}</li>
 *   <li>{@link DuplicateKeyException} from the persistence adapter →
 *       {@code CONFLICT} (Hexagonal ACL: infra exceptions never leak past
 *       the application layer)</li>
 * </ul>
 */
@Service
class RegisterUserService implements RegisterUserUseCase {

    private final UserRepository repo;
    private final ApplicationEventPublisher events;
    private final PasswordService passwordService;

    RegisterUserService(UserRepository repo,
                        ApplicationEventPublisher events,
                        PasswordService passwordService) {
        this.repo = repo;
        this.events = events;
        this.passwordService = passwordService;
    }

    @Override
    @Transactional
    public UserId execute(Command cmd) {
        Objects.requireNonNull(cmd, "cmd");

        // L3: let the password service and aggregate own their invariants;
        // translate rejections into a single application-layer error code.
        String hash;
        User u;
        try {
            hash = passwordService.hash(cmd.password());
            u = User.register(cmd.name(), cmd.email(), hash);
        } catch (NullPointerException | IllegalArgumentException e) {
            throw new AppException(AppCode.VALIDATION_FAILED);
        }

        try {
            repo.save(u);
        } catch (DuplicateKeyException e) {
            throw new AppException(AppCode.CONFLICT);
        }

        events.publishEvent(UserRegistered.of(u.id(), u.email()));
        return u.id();
    }
}
