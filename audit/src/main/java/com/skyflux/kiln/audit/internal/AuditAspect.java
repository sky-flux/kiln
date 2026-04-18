package com.skyflux.kiln.audit.internal;

import cn.dev33.satoken.stp.StpUtil;
import com.skyflux.kiln.audit.api.AuditService;
import com.skyflux.kiln.audit.domain.AuditAction;
import com.skyflux.kiln.audit.domain.AuditResource;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.regex.Pattern;

@Aspect
@Component
class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    private static final Pattern CONFIRM_SHIP_DELIVER =
            Pattern.compile(".+/(confirm|ship|deliver)$");

    private final AuditService auditService;

    AuditAspect(AuditService auditService) { this.auditService = auditService; }

    @AfterReturning(
        pointcut = "within(com.skyflux.kiln..*) && within(@org.springframework.web.bind.annotation.RestController *)",
        returning = "result")
    public void recordSuccess(Object result) {
        HttpServletRequest request = currentRequest();
        if (request == null) return;

        String path   = request.getRequestURI();
        String method = request.getMethod();

        AuditResource resource = resolveResource(path);
        AuditAction   action   = resolveAction(method, path);
        UUID actorId  = resolveActor();
        String requestId = MDC.get("traceId");
        String details = AuditDetailsJson.from(java.util.Map.of("path", path, "method", method));

        auditService.record(resource, action, actorId, null, details, requestId);
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) return null;
        return sra.getRequest();
    }

    static AuditResource resolveResource(String path) {
        if (path.contains("/auth"))     return AuditResource.USER;
        if (path.contains("/members"))  return AuditResource.MEMBER;
        if (path.contains("/roles"))    return AuditResource.ROLE;
        if (path.contains("/tenants"))  return AuditResource.TENANT;
        if (path.contains("/orders"))   return AuditResource.ORDER;
        if (path.contains("/products")) return AuditResource.PRODUCT;
        if (path.contains("/users"))    return AuditResource.USER;
        return AuditResource.SYSTEM;
    }

    static AuditAction resolveAction(String method, String path) {
        // Path-segment overrides take priority over HTTP-method mapping
        if (path.endsWith("/login"))   return AuditAction.LOGIN;
        if (path.endsWith("/logout"))  return AuditAction.LOGOUT;
        if (path.endsWith("/pay"))     return AuditAction.PAY;
        if (path.endsWith("/cancel"))  return AuditAction.CANCEL;
        if (path.endsWith("/points"))  return AuditAction.AWARD_POINTS;
        if (CONFIRM_SHIP_DELIVER.matcher(path).matches()) return AuditAction.UPDATE;
        return switch (method.toUpperCase()) {
            case "GET"           -> AuditAction.READ;
            case "POST"          -> AuditAction.CREATE;
            case "PUT", "PATCH"  -> AuditAction.UPDATE;
            case "DELETE"        -> AuditAction.DELETE;
            default              -> AuditAction.CREATE;
        };
    }

    private static UUID resolveActor() {
        try {
            return StpUtil.isLogin() ? UUID.fromString(StpUtil.getLoginIdAsString()) : null;
        } catch (Exception e) {
            log.debug("Could not resolve actor from Sa-Token context", e);
            return null;
        }
    }
}
