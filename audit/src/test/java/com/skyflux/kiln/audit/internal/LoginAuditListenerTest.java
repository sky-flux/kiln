package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.api.AuditService;
import com.skyflux.kiln.audit.domain.AuditEventType;
import com.skyflux.kiln.user.domain.event.LoginEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link LoginAuditListener}.
 */
@ExtendWith(MockitoExtension.class)
class LoginAuditListenerTest {

    @Mock
    private AuditService auditService;

    @InjectMocks
    private LoginAuditListener listener;

    private static final Instant NOW = Instant.parse("2026-04-18T10:00:00Z");

    @Test
    void loginSuccessRecordsLoginSuccessWithRequestId() {
        UUID userId = UUID.randomUUID();
        LoginEvent.LoginSucceeded event = new LoginEvent.LoginSucceeded(
                new com.skyflux.kiln.user.domain.model.UserId(userId), NOW, "req-abc");

        listener.on(event);

        verify(auditService).record(
                AuditEventType.LOGIN_SUCCESS, userId, userId, null, "req-abc");
    }

    @Test
    void loginFailedWithUnknownEmailRecordsLoginFailedWithNullActor() {
        LoginEvent.LoginFailed event = new LoginEvent.LoginFailed(
                null, "UNKNOWN_EMAIL", NOW, "req-1");

        listener.on(event);

        verify(auditService).record(
                AuditEventType.LOGIN_FAILED, null, null,
                "{\"reason\":\"UNKNOWN_EMAIL\"}", "req-1");
    }

    @Test
    void loginFailedWithWrongPasswordRecordsLoginFailedWithReason() {
        UUID userId = UUID.randomUUID();
        LoginEvent.LoginFailed event = new LoginEvent.LoginFailed(
                userId, "WRONG_PASSWORD", NOW, "req-2");

        listener.on(event);

        // Gate 3 C3: actor is null (caller is unauthenticated pre-auth);
        // target is the account under attack.
        verify(auditService).record(
                AuditEventType.LOGIN_FAILED, null, userId,
                "{\"reason\":\"WRONG_PASSWORD\"}", "req-2");
    }

    @Test
    void loginFailedWithAccountLockedRecordsLoginFailedWithReasonTag() {
        UUID userId = UUID.randomUUID();
        LoginEvent.LoginFailed event = new LoginEvent.LoginFailed(
                userId, "ACCOUNT_LOCKED", NOW, "req-3");

        listener.on(event);

        verify(auditService).record(
                AuditEventType.LOGIN_FAILED, null, userId,
                "{\"reason\":\"ACCOUNT_LOCKED\"}", "req-3");
    }
}
