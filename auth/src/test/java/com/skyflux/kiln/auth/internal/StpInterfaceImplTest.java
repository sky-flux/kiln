package com.skyflux.kiln.auth.internal;

import com.skyflux.kiln.auth.domain.PermissionLookupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Phase 4.2 replacement of the infra-module stub. The real
 * Sa-Token → DB integration is exercised by {@code PermissionJooqRepositoryTest}
 * and the app-level {@code KilnIntegrationTest}; here we pin the
 * loginId → UUID parsing and delegation contract.
 */
@ExtendWith(MockitoExtension.class)
class StpInterfaceImplTest {

    @Mock
    PermissionLookupService lookup;

    @InjectMocks
    StpInterfaceImpl stp;

    @Test
    void getPermissionListParsesLoginIdAndDelegates() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(lookup.permissionsFor(userId)).thenReturn(List.of("user.admin", "user.read"));

        List<String> perms = stp.getPermissionList(userId.toString(), "login");

        assertThat(perms).containsExactly("user.admin", "user.read");
    }

    @Test
    void getRoleListParsesLoginIdAndDelegates() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(lookup.rolesFor(userId)).thenReturn(List.of("ADMIN"));

        List<String> roles = stp.getRoleList(userId.toString(), "login");

        assertThat(roles).containsExactly("ADMIN");
    }

    @Test
    void parseUserIdRejectsNull() {
        // Sa-Token should never call with a null loginId once a session exists,
        // but if someone wires it wrong we want a loud failure rather than a
        // silent empty list that hides a misrouted request.
        assertThatThrownBy(() -> stp.getPermissionList(null, "login"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a String");
        assertThatThrownBy(() -> stp.getRoleList(null, "login"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a String");
    }

    @Test
    void parseUserIdRejectsNonStringLoginId() {
        // Phase 4.2 Gate 3 I1: loginId must be a String (the UUID.toString written by
        // AuthenticateUserService). A non-String here means someone passed a raw Object
        // via StpUtil.login — we refuse rather than String.valueOf-coerce into a bogus UUID.
        assertThatThrownBy(() -> stp.getPermissionList(42, "login"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("java.lang.Integer");
    }

    @Test
    void parseUserIdRejectsNonUuidLoginId() {
        assertThatThrownBy(() -> stp.getPermissionList("not-a-uuid", "login"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
