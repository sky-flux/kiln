package com.skyflux.kiln.user.application.usecase;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.tenant.api.TenantContext;
import com.skyflux.kiln.user.application.port.in.GetUserUseCase;
import com.skyflux.kiln.user.application.port.out.UserRepository;
import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application-layer use case for retrieving a user by ID.
 *
 * <p>Defense-in-depth: even if the DB-level RLS policy is bypassed (e.g.,
 * the connection user has BYPASSRLS/SUPERUSER privileges), the application
 * layer enforces tenant isolation by comparing the user's {@code tenantId}
 * against the current request's {@link TenantContext#CURRENT}. A mismatch
 * returns NOT_FOUND — identical to "user not found", to prevent tenant
 * enumeration across boundaries.
 */
@Service
@Transactional(readOnly = true)
class GetUserService implements GetUserUseCase {

    private final UserRepository repo;

    GetUserService(UserRepository repo) {
        this.repo = repo;
    }

    @Override
    public User execute(UserId id) {
        java.util.Objects.requireNonNull(id, "id");
        User user = repo.findById(id)
                .orElseThrow(() -> new AppException(AppCode.NOT_FOUND));

        // Application-level tenant check — defense-in-depth against RLS bypass.
        // Only enforce when a tenant context is active (authenticated requests
        // always have one; this guard is a no-op for system-admin tooling that
        // runs without a tenant scope).
        if (TenantContext.CURRENT.isBound()) {
            UUID currentTenantId = TenantContext.CURRENT.get();
            if (!currentTenantId.equals(user.tenantId())) {
                throw new AppException(AppCode.NOT_FOUND);
            }
        }

        return user;
    }
}
