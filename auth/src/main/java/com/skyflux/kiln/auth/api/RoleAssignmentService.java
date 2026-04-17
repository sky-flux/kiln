package com.skyflux.kiln.auth.api;

import com.skyflux.kiln.auth.domain.RoleCode;

import java.util.UUID;

/**
 * Public API for granting a role to a user. Exposed in {@code auth.api} so
 * other modules (user, integration tests, future admin endpoints) may import
 * it without crossing into {@code auth.internal}.
 *
 * <p>Intentionally minimal — no revoke, no bulk, no query. Surface extends
 * as use-cases land.
 */
public interface RoleAssignmentService {

    /**
     * Assign the given role to the user. Idempotent — a repeat assignment is
     * a no-op (no duplicate row, no exception).
     */
    void assign(UUID userId, RoleCode role);

    /**
     * Revoke the given role from the user. Idempotent — removing a role the
     * user doesn't hold is a no-op (no exception, no additional side effects).
     */
    void revoke(UUID userId, RoleCode role);
}
