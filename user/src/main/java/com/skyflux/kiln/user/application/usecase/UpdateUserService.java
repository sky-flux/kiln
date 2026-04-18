package com.skyflux.kiln.user.application.usecase;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.tenant.api.TenantContext;
import com.skyflux.kiln.user.application.port.in.UpdateUserUseCase;
import com.skyflux.kiln.user.application.port.out.UserRepository;
import com.skyflux.kiln.user.domain.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Applies mutable field updates to an existing user.
 *
 * <p>Defense-in-depth: applies the same application-layer tenant isolation
 * check as {@code GetUserService} — if the loaded user's tenantId doesn't
 * match the active {@link TenantContext}, the request is rejected as NOT_FOUND
 * to prevent cross-tenant enumeration.
 */
@Service
@Transactional
class UpdateUserService implements UpdateUserUseCase {

    private final UserRepository repo;

    UpdateUserService(UserRepository repo) {
        this.repo = repo;
    }

    @Override
    public User execute(Command cmd) {
        Objects.requireNonNull(cmd, "cmd");
        User user = repo.findById(cmd.userId())
                .orElseThrow(() -> new AppException(AppCode.NOT_FOUND));
        if (TenantContext.CURRENT.isBound()) {
            UUID currentTenantId = TenantContext.CURRENT.get();
            if (!currentTenantId.equals(user.tenantId())) {
                throw new AppException(AppCode.NOT_FOUND);
            }
        }
        User updated = user.withName(cmd.name());
        repo.save(updated);
        return updated;
    }
}
