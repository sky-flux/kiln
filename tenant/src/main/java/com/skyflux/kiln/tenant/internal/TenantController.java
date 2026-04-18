package com.skyflux.kiln.tenant.internal;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.skyflux.kiln.tenant.api.TenantId;
import com.skyflux.kiln.tenant.domain.Tenant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/admin/tenants")
@SaCheckRole("ADMIN")
class TenantController {

    private final TenantService service;

    TenantController(TenantService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TenantResponse create(@Valid @RequestBody CreateTenantRequest req) {
        return TenantResponse.from(service.create(req.code(), req.name()));
    }

    @GetMapping("/{id}")
    TenantResponse get(@PathVariable String id) {
        return TenantResponse.from(service.get(TenantId.of(id)));
    }

    @PutMapping("/{id}")
    TenantResponse update(@PathVariable String id, @Valid @RequestBody UpdateTenantRequest req) {
        return TenantResponse.from(service.update(TenantId.of(id), req.name(), req.status()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void suspend(@PathVariable String id) {
        service.suspend(TenantId.of(id));
    }

    record CreateTenantRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "[a-z0-9-]+") String code,
        @NotBlank @Size(max = 200) String name) {}

    record UpdateTenantRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Pattern(regexp = "ACTIVE|SUSPENDED") String status) {}

    record TenantResponse(String id, String code, String name, String status, Instant createdAt) {
        static TenantResponse from(Tenant t) {
            return new TenantResponse(
                t.id().value().toString(), t.code(), t.name(), t.status(), t.createdAt());
        }
    }
}
