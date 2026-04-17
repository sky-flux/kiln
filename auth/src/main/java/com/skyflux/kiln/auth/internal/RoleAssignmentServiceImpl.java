package com.skyflux.kiln.auth.internal;

import com.skyflux.kiln.auth.api.RoleAssignmentService;
import com.skyflux.kiln.auth.domain.Role;
import com.skyflux.kiln.auth.domain.RoleCode;
import com.skyflux.kiln.auth.repo.RoleJooqRepository;
import com.skyflux.kiln.auth.repo.UserRoleJooqRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/**
 * Looks up a {@link Role} by its {@link RoleCode#value()} then delegates to
 * {@link UserRoleJooqRepository#assign} which relies on the DB's
 * {@code ON CONFLICT DO NOTHING} for idempotency.
 *
 * <p>If the requested {@code RoleCode} isn't present in the {@code roles}
 * catalogue (only possible if someone hand-deletes seeded rows or extends
 * {@code RoleCode} without a migration) we fail loudly rather than assign a
 * "silently missing" role.
 */
@Service
class RoleAssignmentServiceImpl implements RoleAssignmentService {

    private final RoleJooqRepository roles;
    private final UserRoleJooqRepository userRoles;

    RoleAssignmentServiceImpl(RoleJooqRepository roles, UserRoleJooqRepository userRoles) {
        this.roles = roles;
        this.userRoles = userRoles;
    }

    @Override
    public void assign(UUID userId, RoleCode role) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");

        Role found = roles.findByCode(role.value())
                .orElseThrow(() -> new IllegalStateException(
                        "Role not seeded in catalogue: " + role.value()));
        userRoles.assign(userId, found.id());
    }
}
