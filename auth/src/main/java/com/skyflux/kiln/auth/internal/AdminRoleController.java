package com.skyflux.kiln.auth.internal;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.skyflux.kiln.auth.api.RoleAssignmentService;
import com.skyflux.kiln.auth.domain.RoleCode;
import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin-only endpoints for the role catalogue side of the RBAC stack.
 *
 * <p>Protected by {@code @SaCheckRole("ADMIN")} — non-admin callers hit
 * Sa-Token's filter chain and the request is translated to 403 by
 * {@code GlobalExceptionHandler}.
 *
 * <h3>Why here, not in {@code user/adapter/in/web/AdminController}</h3>
 *
 * The ideal resting place for these endpoints would be next to the existing
 * user-count admin endpoint, since all three share the {@code /api/v1/admin}
 * prefix and the {@code @SaCheckRole("ADMIN")} guard. That requires the
 * {@code user} module to depend on {@code auth.api.RoleAssignmentService} —
 * but the {@code auth} module ALREADY depends on {@code user} (for
 * {@code UserRegistered} event consumption in
 * {@link UserRegisteredListener}). Gradle rejects the resulting cycle.
 *
 * <p>Breaking the cycle would require moving {@code UserRegistered} out of
 * {@code user} (into {@code common} or its own contract module), which is
 * out of scope for Phase 4.3 Wave 2. The assign/revoke endpoints therefore
 * live in the auth module where the service they delegate to is defined.
 *
 * <p>When Phase 5 introduces the Modulith events-publication registry (per
 * {@code docs/design.md} Ch 19.9) {@code UserRegistered} should be promoted
 * to a contract type, at which point all admin endpoints can be consolidated
 * into a single controller in the {@code user} module (or split into a
 * dedicated {@code admin} module).
 */
@RestController
@RequestMapping("/api/v1/admin")
class AdminRoleController {

    private final RoleAssignmentService roleAssignment;

    AdminRoleController(RoleAssignmentService roleAssignment) {
        this.roleAssignment = roleAssignment;
    }

    @PostMapping("/users/{userId}/roles/{roleCode}")
    @SaCheckRole("ADMIN")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignRole(@PathVariable UUID userId, @PathVariable String roleCode) {
        RoleCode code = parseOrThrow(roleCode);
        roleAssignment.assign(userId, code);
    }

    @DeleteMapping("/users/{userId}/roles/{roleCode}")
    @SaCheckRole("ADMIN")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeRole(@PathVariable UUID userId, @PathVariable String roleCode) {
        RoleCode code = parseOrThrow(roleCode);
        roleAssignment.revoke(userId, code);
    }

    private static RoleCode parseOrThrow(String raw) {
        try {
            return RoleCode.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new AppException(AppCode.VALIDATION_FAILED, "unknown role code: " + raw);
        }
    }
}
