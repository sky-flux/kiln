package com.skyflux.kiln.auth.internal;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.skyflux.kiln.auth.domain.Role;
import com.skyflux.kiln.tenant.api.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin-only REST endpoints for dynamic role management.
 *
 * <p>Protected by {@code @SaCheckRole("ADMIN")} at the method level so the
 * AOP aspect enforces the guard on every request. The {@code @SaCheckRole}
 * annotation requires {@code sa-token-spring-aop} on the classpath — which
 * is already declared in {@code infra/build.gradle}.
 *
 * <p>All mutations are scoped to the tenant resolved from
 * {@link TenantContext#CURRENT} which is bound per-request by
 * {@code TenantFilter}.
 */
@RestController
@RequestMapping("/api/v1/admin/roles")
class RoleCrudController {

    private final RoleCrudService service;

    RoleCrudController(RoleCrudService service) {
        this.service = service;
    }

    @PostMapping
    @SaCheckRole("ADMIN")
    @ResponseStatus(HttpStatus.CREATED)
    RoleResponse create(@Valid @RequestBody CreateRoleRequest req) {
        UUID tenantId = TenantContext.CURRENT.get();
        return RoleResponse.from(service.createRole(tenantId, req.code(), req.name()));
    }

    @GetMapping
    @SaCheckRole("ADMIN")
    List<RoleResponse> list() {
        return service.listRoles().stream().map(RoleResponse::from).toList();
    }

    @DeleteMapping("/{id}")
    @SaCheckRole("ADMIN")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.deleteRole(id);
    }

    record CreateRoleRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 100) String name) {
    }

    record RoleResponse(String id, String code, String name) {
        static RoleResponse from(Role r) {
            return new RoleResponse(r.id().toString(), r.code(), r.name());
        }
    }
}
