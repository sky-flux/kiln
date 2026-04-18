package com.skyflux.kiln.tenant.config;

import cn.dev33.satoken.session.SaTerminalInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.tenant.api.TenantContext;
import com.skyflux.kiln.tenant.repo.TenantJooqRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that resolves the current tenant and binds it to
 * {@link TenantContext#CURRENT} (a Java {@link ScopedValue}) for the
 * duration of the request.
 *
 * <p>Resolution order:
 * <ol>
 *   <li><b>Bearer token</b> — reads the {@code tenantId} extra from the Sa-Token
 *       terminal session via {@code getTerminalInfoByToken()}. This path works in
 *       filter scope because it reads directly from the session store (Redis)
 *       without requiring {@code SaHolder} to be initialized by the MVC
 *       interceptor.</li>
 *   <li><b>X-Tenant-Code header</b> — looks up the tenant by code in the DB.
 *       Used for anonymous requests (register, login) where no session exists
 *       yet.</li>
 *   <li><b>Neither present</b> — no tenant scope is set; the request proceeds
 *       without RLS enforcement (e.g. health-check, actuator endpoints).</li>
 * </ol>
 */
@Order(1)
class TenantFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final TenantJooqRepository tenantRepo;

    TenantFilter(TenantJooqRepository tenantRepo) { this.tenantRepo = tenantRepo; }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        UUID tenantId = resolveTenantId(request);
        if (tenantId == null) {
            chain.doFilter(request, response);
            return;
        }
        runInTenantScope(tenantId, request, response, chain);
    }

    private UUID resolveTenantId(HttpServletRequest request) {
        // 1. Try Bearer token — reads from Redis session store directly,
        //    no SaHolder initialization required.
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            try {
                SaTerminalInfo terminalInfo = StpUtil.stpLogic.getTerminalInfoByToken(token);
                if (terminalInfo != null) {
                    Object tenantId = terminalInfo.getExtra("tenantId");
                    if (tenantId != null) return UUID.fromString(tenantId.toString());
                }
            } catch (Exception e) {
                // "Not logged in" sub-types (NotLoginException) are expected during pre-auth requests;
                // infrastructure failures (RedisConnectionException, etc.) should be observable.
                log.warn("TenantFilter: bearer token resolution failed [{}] — falling through to X-Tenant-Code",
                        e.getClass().getSimpleName());
            }
        }

        // 2. Try X-Tenant-Code header — used for anonymous requests.
        String code = request.getHeader("X-Tenant-Code");
        if (code == null) return null;

        return tenantRepo.findByCode(code)
                .map(t -> t.id().value())
                .orElseThrow(() -> new AppException(AppCode.NOT_FOUND, "Unknown tenant: " + code));
    }

    private void runInTenantScope(UUID tenantId, HttpServletRequest req,
                                  HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        try {
            ScopedValue.where(TenantContext.CURRENT, tenantId).run(() -> {
                try {
                    chain.doFilter(req, res);
                } catch (Exception e) { throw new RuntimeException(e); }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioe) throw ioe;
            if (e.getCause() instanceof ServletException se) throw se;
            throw e;
        }
    }
}
