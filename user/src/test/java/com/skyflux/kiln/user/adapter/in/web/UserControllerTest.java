package com.skyflux.kiln.user.adapter.in.web;

import com.skyflux.kiln.user.application.port.in.GetUserUseCase;
import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Autowired
    MockMvc mvc;

    @Test
    void get_by_id_returns_200_with_user_payload() throws Exception {
        UUID uuid = UUID.randomUUID();
        UserId id = new UserId(uuid);
        User u = User.reconstitute(id, "Alice", "alice@example.com");
        when(useCase.execute(any(UserId.class))).thenReturn(u);

        mvc.perform(get("/api/v1/users/{id}", uuid.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(uuid.toString()))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }
}
