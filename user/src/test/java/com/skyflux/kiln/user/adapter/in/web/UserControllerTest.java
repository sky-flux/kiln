package com.skyflux.kiln.user.adapter.in.web;

import com.skyflux.kiln.user.application.port.in.AuthenticateUserUseCase;
import com.skyflux.kiln.user.application.port.in.CountUsersUseCase;
import com.skyflux.kiln.user.application.port.in.GetUserUseCase;
import com.skyflux.kiln.user.application.port.in.RegisterUserUseCase;
import com.skyflux.kiln.user.domain.model.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link UserController}.
 *
 * <p>Note: the GET {id} happy-path lives in {@code KilnIntegrationTest} instead
 * of this slice — once {@code @SaCheckLogin} is on the GET handler, the
 * Sa-Token auth interceptor is invoked, which requires the full Sa-Token
 * session store (Redis) to be wired. The @WebMvcTest slice does not bring up
 * Sa-Token's infrastructure, so an authenticated GET is not a reasonable
 * slice-level assertion — the integration test is the right layer.
 *
 * <p>POST is still tested here because register does NOT require a logged-in
 * session (it's the bootstrap path that creates a user in the first place).
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    /** Minimal boot configuration so {@code @WebMvcTest} can locate a SpringBootConfiguration. */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackageClasses = UserController.class)
    static class BootConfig {
    }

    /**
     * Spring Test 6.2+ / Boot 4 {@code @MockitoBean} replaces the old
     * {@code @TestConfiguration} + static mock pattern — the mock is reset
     * between tests automatically, avoiding cross-test contamination.
     */
    @MockitoBean
    GetUserUseCase useCase;

    @MockitoBean
    RegisterUserUseCase registerUseCase;

    /**
     * {@code AuthController} lives in the same package and is auto-scanned by
     * {@link BootConfig}. Even though this slice test exercises only
     * {@code UserController}, the context still instantiates {@code AuthController},
     * which requires an {@code AuthenticateUserUseCase} bean. Providing a mock
     * keeps the slice focused without dragging the full auth stack in.
     */
    @MockitoBean
    AuthenticateUserUseCase authenticateUseCase;

    /**
     * Phase 4.2: {@code AdminController} lives in the same package and is
     * auto-scanned. It constructor-injects {@link CountUsersUseCase}; supply a
     * mock so the slice context loads.
     */
    @MockitoBean
    CountUsersUseCase countUsers;

    @Autowired
    MockMvc mvc;

    @Test
    void post_registers_user_and_returns_201_with_generated_id() throws Exception {
        UUID uuid = UUID.randomUUID();
        UserId id = new UserId(uuid);
        when(registerUseCase.execute(any(RegisterUserUseCase.Command.class))).thenReturn(id);

        String body = """
                {"name":"Alice","email":"alice@example.com","password":"S3cret-pw"}
                """;

        mvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(uuid.toString()))
                // I4: UserIdResponse must expose ONLY id — do not leak name/email
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void post_with_blank_name_returns_400() throws Exception {
        String body = """
                {"name":"","email":"alice@example.com","password":"S3cret-pw"}
                """;

        mvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_with_invalid_email_returns_400() throws Exception {
        String body = """
                {"name":"Alice","email":"not-an-email","password":"S3cret-pw"}
                """;

        mvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_with_blank_password_returns_400() throws Exception {
        String body = """
                {"name":"Alice","email":"alice@example.com","password":""}
                """;

        mvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
