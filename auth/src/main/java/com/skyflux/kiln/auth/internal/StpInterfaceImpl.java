package com.skyflux.kiln.auth.internal;

import cn.dev33.satoken.stp.StpInterface;
import com.skyflux.kiln.auth.domain.PermissionLookupService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Phase 4.2 replacement for the Phase-4 stub that used to live in
 * {@code infra/security/}. Sa-Token calls this on every protected request
 * to answer {@code @SaCheckRole} / {@code @SaCheckPermission} checks.
 *
 * <p>Sa-Token's login identity is the user UUID string (set in
 * {@code AuthenticateUserService.execute} via
 * {@code StpUtil.login(user.id().value().toString())}). This impl parses
 * it back into a {@link UUID} and routes the lookup through
 * {@link PermissionLookupService}.
 *
 * <p>A null or non-UUID {@code loginId} is an invariant violation (Sa-Token
 * should never call with a null loginId once a session exists, and we
 * never write a non-UUID identity), so we let the caller see a loud
 * failure rather than returning an empty list that would silently mask a
 * misrouted request.
 */
@Component
@org.springframework.context.annotation.Primary
class StpInterfaceImpl implements StpInterface {

    private final PermissionLookupService lookup;

    StpInterfaceImpl(PermissionLookupService lookup) {
        this.lookup = lookup;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return lookup.permissionsFor(parseUserId(loginId));
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return lookup.rolesFor(parseUserId(loginId));
    }

    private static UUID parseUserId(Object loginId) {
        // Sa-Token passes loginId as-is from StpUtil.login(...). The AuthenticateUserService
        // path writes a UUID.toString(), so a non-String here means either a test is using
        // StpUtil.login(someNonStringId) (unsupported) or Sa-Token's internal contract changed.
        // Either way, fail loudly — don't coerce via String.valueOf and produce a misleading
        // "invalid UUID: null" error.
        if (!(loginId instanceof String s)) {
            throw new IllegalStateException(
                    "loginId is not a String (got " + (loginId == null ? "null" : loginId.getClass().getName()) + ")");
        }
        return UUID.fromString(s);
    }
}
