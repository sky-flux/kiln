package com.skyflux.kiln.user.application.usecase;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
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
 * <p>Transaction boundary lives here (design.md Ch 5.7). On save, a
 * {@link DuplicateKeyException} from the persistence adapter is translated
 * to {@link AppException} with {@link AppCode#CONFLICT} — Hexagonal ACL
 * pattern: infrastructure exceptions never leak past the application layer.
 */
@Service
class RegisterUserService implements RegisterUserUseCase {

    private final UserRepository repo;
    private final ApplicationEventPublisher events;

    RegisterUserService(UserRepository repo, ApplicationEventPublisher events) {
        this.repo = repo;
        this.events = events;
    }

    @Override
    @Transactional
    public UserId execute(Command cmd) {
        Objects.requireNonNull(cmd, "cmd");

        // L3: let the aggregate own its invariants; translate domain-layer
        // rejections into the application-layer error code in one place.
        User u;
        try {
            u = User.register(cmd.name(), cmd.email());
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
