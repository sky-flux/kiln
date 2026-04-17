package com.skyflux.kiln.infra.security;

import cn.dev33.satoken.stp.StpInterface;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token's role / permission lookup SPI.
 *
 * <p>Phase 4 stub — returns empty lists for every caller. Phase 4.1 will
 * replace this with a RBAC lookup against the {@code users}/{@code roles}/
 * {@code user_roles} tables added by migrations V3+.
 */
@Component
class StpInterfaceImpl implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return List.of();
    }
}
