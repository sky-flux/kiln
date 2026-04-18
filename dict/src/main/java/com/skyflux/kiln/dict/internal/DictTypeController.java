package com.skyflux.kiln.dict.internal;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.skyflux.kiln.common.result.R;
import com.skyflux.kiln.dict.domain.DictType;
import com.skyflux.kiln.tenant.api.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
class DictTypeController {

    private final DictService service;

    DictTypeController(DictService service) { this.service = service; }

    @GetMapping("/api/v1/dict/types")
    @SaCheckLogin
    R<List<DictTypeResponse>> list() {
        return R.ok(service.listTypes().stream().map(DictTypeResponse::from).toList());
    }

    @PostMapping("/api/v1/admin/dict/types")
    @SaCheckRole("ADMIN")
    @ResponseStatus(HttpStatus.CREATED)
    R<DictTypeResponse> create(@Valid @RequestBody CreateDictTypeRequest req) {
        UUID tenantId = TenantContext.CURRENT.get();
        return R.ok(DictTypeResponse.from(service.createType(req.code(), req.name(), tenantId)));
    }

    record CreateDictTypeRequest(
        @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Z][A-Z0-9_]*") String code,
        @NotBlank @Size(max = 100) String name) {}

    record DictTypeResponse(String id, String code, String name, boolean isSystem) {
        static DictTypeResponse from(DictType t) {
            return new DictTypeResponse(t.id().toString(), t.code(), t.name(), t.isSystem());
        }
    }
}
