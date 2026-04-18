package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.api.AuditService;
import com.skyflux.kiln.audit.domain.AuditAction;
import com.skyflux.kiln.audit.domain.AuditResource;
import com.skyflux.kiln.user.domain.event.LoginEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    void loginSuccessRecordsUserLoginWithResultSuccess() {
        UUID userId = UUID.randomUUID();
        LoginEvent.LoginSucceeded event = new LoginEvent.LoginSucceeded(
                new com.skyflux.kiln.user.domain.model.UserId(userId), NOW, "req-abc");

        listener.on(event);

        verify(auditService).record(
                eq(AuditResource.USER), eq(AuditAction.LOGIN),
                eq(userId), eq(userId),
                argThat(d -> d != null && d.contains("SUCCESS")),
                eq("req-abc"));
    }

    @Test
    void loginFailedWithUnknownEmailRecordsUserLoginWithNullActor() {
        LoginEvent.LoginFailed event = new LoginEvent.LoginFailed(
                null, "UNKNOWN_EMAIL", NOW, "req-1");

        listener.on(event);

        verify(auditService).record(
                eq(AuditResource.USER), eq(AuditAction.LOGIN),
                isNull(), isNull(),
                argThat(d -> d != null && d.contains("FAILED") && d.contains("UNKNOWN_EMAIL")),
                eq("req-1"));
    }

    @Test
    void loginFailedWithWrongPasswordRecordsUserLoginWithReason() {
        UUID userId = UUID.randomUUID();
        LoginEvent.LoginFailed event = new LoginEvent.LoginFailed(
                userId, "WRONG_PASSWORD", NOW, "req-2");

        listener.on(event);

        // actor is null (caller is unauthenticated pre-auth); target is the account under attack.
        verify(auditService).record(
                eq(AuditResource.USER), eq(AuditAction.LOGIN),
                isNull(), eq(userId),
                argThat(d -> d != null && d.contains("FAILED") && d.contains("WRONG_PASSWORD")),
                eq("req-2"));
    }

    @Test
    void loginFailedWithAccountLockedRecordsUserLoginWithReason() {
        UUID userId = UUID.randomUUID();
        LoginEvent.LoginFailed event = new LoginEvent.LoginFailed(
                userId, "ACCOUNT_LOCKED", NOW, "req-3");

        listener.on(event);

        verify(auditService).record(
                eq(AuditResource.USER), eq(AuditAction.LOGIN),
                isNull(), eq(userId),
                argThat(d -> d != null && d.contains("FAILED") && d.contains("ACCOUNT_LOCKED")),
                eq("req-3"));
    }
}
