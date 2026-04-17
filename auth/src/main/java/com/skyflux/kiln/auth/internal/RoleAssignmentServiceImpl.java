package com.skyflux.kiln.auth.internal;

import com.skyflux.kiln.auth.api.RoleAssignmentService;
import com.skyflux.kiln.auth.domain.Role;
import com.skyflux.kiln.auth.domain.RoleCode;
import com.skyflux.kiln.auth.domain.event.RoleEvent;
import com.skyflux.kiln.auth.repo.RoleJooqRepository;
import com.skyflux.kiln.auth.repo.UserRoleJooqRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
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
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    RoleAssignmentServiceImpl(RoleJooqRepository roles,
                              UserRoleJooqRepository userRoles,
                              ApplicationEventPublisher publisher,
                              Clock clock) {
        this.roles = roles;
        this.userRoles = userRoles;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Override
    public void assign(UUID userId, RoleCode role) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");

        Role found = roles.findByCode(role.value())
                .orElseThrow(() -> new IllegalStateException(
                        "Role not seeded in catalogue: " + role.value()));
        userRoles.assign(userId, found.id());
        // Publish AFTER the DB write succeeds. An idempotent no-op assign
        // (existing row -> ON CONFLICT DO NOTHING) still publishes; listeners
        // must be idempotent. See RoleEvent class javadoc.
        publisher.publishEvent(new RoleEvent.RoleAssigned(userId, role, clock.instant()));
    }

    @Override
    public void revoke(UUID userId, RoleCode role) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");

        Role found = roles.findByCode(role.value())
                .orElseThrow(() -> new IllegalStateException(
                        "Role not seeded in catalogue: " + role.value()));
        userRoles.revoke(userId, found.id());
        publisher.publishEvent(new RoleEvent.RoleRevoked(userId, role, clock.instant()));
    }
}
