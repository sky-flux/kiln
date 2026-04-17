package com.skyflux.kiln.infra.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the Phase-4 stub {@link StpInterfaceImpl} — Sa-Token's
 * role/permission lookup plugged in as an empty-list placeholder until
 * Phase 4.1 wires it to real RBAC data.
 */
class StpInterfaceImplTest {

    private final StpInterfaceImpl stp = new StpInterfaceImpl();

    @Test
    void returnsEmptyPermissionList() {
        assertThat(stp.getPermissionList("user-1", "login")).isEmpty();
    }

    @Test
    void returnsEmptyRoleList() {
        assertThat(stp.getRoleList("user-1", "login")).isEmpty();
    }

    @Test
    void returnsEmptyForNullLoginId() {
        assertThat(stp.getPermissionList(null, "login")).isEmpty();
        assertThat(stp.getRoleList(null, "login")).isEmpty();
    }
}
