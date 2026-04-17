package com.skyflux.kiln.auth.internal;

import com.skyflux.kiln.auth.api.RoleAssignmentService;
import com.skyflux.kiln.auth.domain.RoleCode;
import com.skyflux.kiln.infra.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link AdminRoleController}.
 *
 * <p>The {@code @SaCheckRole("ADMIN")} guard is enforced by Sa-Token's AOP
 * which is not loaded into the {@code @WebMvcTest} slice context — the full
 * login→role check→403 / 204 flow is covered by {@code KilnIntegrationTest}
 * in Wave 3. This slice pins only:
 *
 * <ul>
 *   <li>HTTP verb / path wiring (POST and DELETE under
 *       {@code /api/v1/admin/users/{userId}/roles/{roleCode}}).</li>
 *   <li>Path variable parsing into {@code UUID} + {@link RoleCode}.</li>
 *   <li>Delegation to {@link RoleAssignmentService#assign} /
 *       {@link RoleAssignmentService#revoke}.</li>
 *   <li>Unknown role codes short-circuit with 400 without reaching the service.</li>
 *   <li>Successful calls return 204 No Content (idempotently for revoke —
 *       the service handles the "already absent" case, the controller does not
 *       special-case).</li>
 * </ul>
 */
@WebMvcTest(AdminRoleController.class)
class AdminRoleControllerTest {

    /**
     * Minimal boot config so {@code @WebMvcTest} finds a {@code @SpringBootConfiguration}.
     *
     * <p>The {@code auth.internal} package also hosts {@code StpInterfaceImpl},
     * {@code PermissionLookupServiceImpl}, and {@code UserRegisteredListener} —
     * all of which transitively require jOOQ + Spring Data Redis beans that
     * the slice context doesn't bring up. The include filter restricts the
     * component scan to the single controller under test; {@link RoleAssignmentService}
     * is supplied as a {@link MockitoBean}.
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(
            basePackageClasses = AdminRoleController.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = AdminRoleController.class))
    @Import(GlobalExceptionHandler.class)
    static class BootConfig {
    }

    @MockitoBean
    RoleAssignmentService roleAssignment;

    @Autowired
    MockMvc mvc;

    @Test
    void assignRole_returns204_andInvokes_service() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        mvc.perform(post("/api/v1/admin/users/{userId}/roles/{roleCode}", userId, "ADMIN"))
                .andExpect(status().isNoContent());

        verify(roleAssignment).assign(userId, RoleCode.ADMIN);
    }

    @Test
    void assignRole_withUnknownCode_returns400_andServiceNotInvoked() throws Exception {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        mvc.perform(post("/api/v1/admin/users/{userId}/roles/{roleCode}", userId, "GODMODE"))
                .andExpect(status().isBadRequest());

        verify(roleAssignment, never()).assign(userId, null);
    }

    @Test
    void revokeRole_returns204_andInvokes_service() throws Exception {
        UUID userId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        mvc.perform(delete("/api/v1/admin/users/{userId}/roles/{roleCode}", userId, "USER"))
                .andExpect(status().isNoContent());

        verify(roleAssignment).revoke(userId, RoleCode.USER);
    }

    @Test
    void revokeRole_isIdempotent_whenAssignmentAbsent() throws Exception {
        UUID userId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        // Mock service returns normally (idempotent contract). Controller must
        // surface 204 regardless of whether the row existed at the DB.
        mvc.perform(delete("/api/v1/admin/users/{userId}/roles/{roleCode}", userId, "ADMIN"))
                .andExpect(status().isNoContent());

        verify(roleAssignment).revoke(userId, RoleCode.ADMIN);
    }

    @Test
    void revokeRole_withUnknownCode_returns400_andServiceNotInvoked() throws Exception {
        UUID userId = UUID.fromString("55555555-5555-5555-5555-555555555555");

        mvc.perform(delete("/api/v1/admin/users/{userId}/roles/{roleCode}", userId, "NOPE"))
                .andExpect(status().isBadRequest());

        verify(roleAssignment, never()).revoke(userId, null);
    }
}
