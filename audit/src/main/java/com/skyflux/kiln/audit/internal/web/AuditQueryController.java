package com.skyflux.kiln.audit.internal.web;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.skyflux.kiln.audit.api.AuditQueryService;
import com.skyflux.kiln.audit.domain.Audit;
import com.skyflux.kiln.audit.domain.AuditType;
import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin-only REST surface for {@link AuditQueryService}.
 *
 * <p>Deliberately lives inside the {@code audit} module — user/auth don't need
 * to depend on audit for the query side, which keeps the Modulith dependency
 * graph minimal (audit depends on common+infra only).
 *
 * <p>{@code @SaCheckRole("ADMIN")} enforcement is AOP-wired via the auth module's
 * Sa-Token starter; the {@code @WebMvcTest} slice test cannot exercise it (same
 * constraint as {@code AdminController}) — end-to-end role enforcement is
 * covered by {@code KilnIntegrationTest}.
 */
@RestController
@RequestMapping("/api/v1/admin/audit-events")
@Validated
public class AuditQueryController {

    private final AuditQueryService queryService;

    public AuditQueryController(AuditQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @SaCheckRole("ADMIN")
    public PageResult<Audit> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size,
            @RequestParam(required = false) AuditType type,
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) UUID targetUserId) {
        PageQuery query = new PageQuery(page, size, null);
        return queryService.list(query, type, actorUserId, targetUserId);
    }
}
