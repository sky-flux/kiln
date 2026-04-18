package com.skyflux.kiln.user.application.usecase;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.tenant.api.TenantContext;
import com.skyflux.kiln.user.application.port.in.DeleteUserUseCase;
import com.skyflux.kiln.user.application.port.out.UserRepository;
import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Soft-deletes a user by transitioning status to INACTIVE.
 *
 * <p>Defense-in-depth: applies the same application-layer tenant isolation
 * check as {@code GetUserService} — if the loaded user's tenantId doesn't
 * match the active {@link TenantContext}, the request is rejected as NOT_FOUND
 * to prevent cross-tenant enumeration.
 */
@Service
@Transactional
class DeleteUserService implements DeleteUserUseCase {

    private final UserRepository repo;

    DeleteUserService(UserRepository repo) {
        this.repo = repo;
    }

    @Override
    public void execute(UserId userId) {
        Objects.requireNonNull(userId, "userId");
        User user = repo.findById(userId)
                .orElseThrow(() -> new AppException(AppCode.NOT_FOUND));
        if (TenantContext.CURRENT.isBound()) {
            UUID currentTenantId = TenantContext.CURRENT.get();
            if (!currentTenantId.equals(user.tenantId())) {
                throw new AppException(AppCode.NOT_FOUND);
            }
        }
        repo.save(user.deactivate());
    }
}
