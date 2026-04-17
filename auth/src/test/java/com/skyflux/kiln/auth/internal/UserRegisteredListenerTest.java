package com.skyflux.kiln.auth.internal;

import com.skyflux.kiln.auth.api.RoleAssignmentService;
import com.skyflux.kiln.auth.domain.RoleCode;
import com.skyflux.kiln.user.domain.event.UserRegistered;
import com.skyflux.kiln.user.domain.model.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

/**
 * Unit test for the Spring Modulith event listener that auto-assigns the
 * default USER role on registration.
 *
 * <p>The {@code @ApplicationModuleListener} AFTER_COMMIT behavior is
 * integration-level — exercised end-to-end by the app-module
 * {@code KilnIntegrationTest}. Here we only verify the delegation contract
 * so that a refactor can't accidentally drop the USER assignment.
 */
@ExtendWith(MockitoExtension.class)
class UserRegisteredListenerTest {

    @Mock
    RoleAssignmentService roles;

    @InjectMocks
    UserRegisteredListener listener;

    @Test
    void assignsUserRoleOnRegistration() {
        UUID userIdValue = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UserRegistered event = UserRegistered.of(new UserId(userIdValue), "alice@example.com");

        listener.on(event);

        verify(roles).assign(userIdValue, RoleCode.USER);
    }
}
