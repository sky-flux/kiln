package com.skyflux.kiln.auth.internal;

import com.skyflux.kiln.auth.domain.Role;
import com.skyflux.kiln.auth.domain.RoleCode;
import com.skyflux.kiln.auth.domain.event.RoleEvent;
import com.skyflux.kiln.auth.repo.RoleJooqRepository;
import com.skyflux.kiln.auth.repo.UserRoleJooqRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RoleAssignmentServiceImpl}. Mocks the repos and event
 * publisher; verifies catalogue lookup + delegation for both {@code assign}
 * and {@code revoke}, null-guards, the invariant that a missing role code in
 * the catalogue fails loudly, and that Wave 3 role-lifecycle events are
 * published only AFTER the repo write succeeds.
 */
@ExtendWith(MockitoExtension.class)
class RoleAssignmentServiceImplTest {

    @Mock
    RoleJooqRepository roles;

    @Mock
    UserRoleJooqRepository userRoles;

    @Mock
    ApplicationEventPublisher publisher;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant FIXED = Instant.parse("2026-04-18T10:00:00Z");

    private final Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);

    private RoleAssignmentServiceImpl service() {
        return new RoleAssignmentServiceImpl(roles, userRoles, publisher, clock);
    }

    @Test
    void revokeLooksUpRoleByCodeAndDelegates() {
        when(roles.findByCode("ADMIN"))
                .thenReturn(Optional.of(new Role(ADMIN_ROLE_ID, "ADMIN", "Administrator")));

        service().revoke(USER_ID, RoleCode.ADMIN);

        verify(userRoles).revoke(USER_ID, ADMIN_ROLE_ID);
    }

    @Test
    void revokeThrowsWhenRoleMissingFromCatalogue() {
        when(roles.findByCode("ADMIN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().revoke(USER_ID, RoleCode.ADMIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN");

        verify(userRoles, never()).revoke(any(), any());
    }

    @Test
    void revokeRejectsNullUserId() {
        assertThatNullPointerException()
                .isThrownBy(() -> service().revoke(null, RoleCode.ADMIN));
    }

    @Test
    void revokeRejectsNullRole() {
        assertThatNullPointerException()
                .isThrownBy(() -> service().revoke(USER_ID, null));
    }

    // --- Wave 3: role lifecycle events ---

    @Test
    void assignPublishesRoleAssignedEvent() {
        when(roles.findByCode("ADMIN"))
                .thenReturn(Optional.of(new Role(ADMIN_ROLE_ID, "ADMIN", "Administrator")));

        service().assign(USER_ID, RoleCode.ADMIN);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(RoleEvent.RoleAssigned.class);
        RoleEvent.RoleAssigned ev = (RoleEvent.RoleAssigned) captor.getValue();
        assertThat(ev.userId()).isEqualTo(USER_ID);
        assertThat(ev.role()).isEqualTo(RoleCode.ADMIN);
        assertThat(ev.occurredAt()).isEqualTo(FIXED);
    }

    @Test
    void revokePublishesRoleRevokedEvent() {
        when(roles.findByCode("ADMIN"))
                .thenReturn(Optional.of(new Role(ADMIN_ROLE_ID, "ADMIN", "Administrator")));

        service().revoke(USER_ID, RoleCode.ADMIN);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(RoleEvent.RoleRevoked.class);
        RoleEvent.RoleRevoked ev = (RoleEvent.RoleRevoked) captor.getValue();
        assertThat(ev.userId()).isEqualTo(USER_ID);
        assertThat(ev.role()).isEqualTo(RoleCode.ADMIN);
        assertThat(ev.occurredAt()).isEqualTo(FIXED);
    }

    @Test
    void assignDoesNotPublishWhenRepoThrows() {
        when(roles.findByCode("ADMIN"))
                .thenReturn(Optional.of(new Role(ADMIN_ROLE_ID, "ADMIN", "Administrator")));
        doThrow(new RuntimeException("db down"))
                .when(userRoles).assign(USER_ID, ADMIN_ROLE_ID);

        assertThatThrownBy(() -> service().assign(USER_ID, RoleCode.ADMIN))
                .isInstanceOf(RuntimeException.class);

        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void revokeDoesNotPublishWhenRepoThrows() {
        when(roles.findByCode("ADMIN"))
                .thenReturn(Optional.of(new Role(ADMIN_ROLE_ID, "ADMIN", "Administrator")));
        doThrow(new RuntimeException("db down"))
                .when(userRoles).revoke(USER_ID, ADMIN_ROLE_ID);

        assertThatThrownBy(() -> service().revoke(USER_ID, RoleCode.ADMIN))
                .isInstanceOf(RuntimeException.class);

        verify(publisher, never()).publishEvent(any());
    }

    // any() helper — avoid dragging Mockito.any to the top-level imports just for
    // two usages below; keep the imports tight.
    private static <T> T any() { return org.mockito.ArgumentMatchers.any(); }
}
