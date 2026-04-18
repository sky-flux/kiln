package com.skyflux.kiln.user.application.usecase;

import cn.dev33.satoken.stp.StpUtil;
import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.common.security.PasswordService;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.infra.security.SecurityProperties;
import com.skyflux.kiln.user.application.port.in.AuthenticateUserUseCase;
import com.skyflux.kiln.user.application.port.out.UserRepository;
import com.skyflux.kiln.user.domain.event.LoginEvent;
import com.skyflux.kiln.user.domain.exception.AccountLockedException;
import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticateUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-04-18T10:15:30Z");
    private static final UUID TENANT_ID = Ids.next();

    @Mock
    UserRepository repo;

    @Mock
    PasswordService passwordService;

    @Mock
    ApplicationEventPublisher events;

    @Spy
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Spy
    SecurityProperties securityProps = new SecurityProperties(5, Duration.ofMinutes(15), null);

    @InjectMocks
    AuthenticateUserService service;

    @Test
    void null_command_rejected_with_NPE() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.execute(null));
    }

    @Test
    void unknown_email_returns_LOGIN_FAILED_without_leaking_cause() {
        when(repo.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        AuthenticateUserUseCase.Command cmd =
                new AuthenticateUserUseCase.Command("nobody@example.com", "whatever");

        assertThatThrownBy(() -> service.execute(cmd))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).appCode())
                .isEqualTo(AppCode.LOGIN_FAILED);
    }

    @Test
    void email_is_normalized_to_lowercase_before_lookup() {
        when(repo.findByEmail("alice@example.com")).thenReturn(Optional.empty());

        AuthenticateUserUseCase.Command cmd =
                new AuthenticateUserUseCase.Command("  ALICE@Example.COM  ", "whatever");

        assertThatThrownBy(() -> service.execute(cmd))
                .isInstanceOf(AppException.class);

        verify(repo).findByEmail("alice@example.com");
    }

    @Test
    void wrong_password_returns_LOGIN_FAILED() {
        UserId id = UserId.newId();
        User user = User.reconstitute(id, TENANT_ID, "Alice", "alice@example.com", "stored-hash", 0, null, "ACTIVE");
        when(repo.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordService.verify("bad-pw", "stored-hash")).thenReturn(false);

        AuthenticateUserUseCase.Command cmd =
                new AuthenticateUserUseCase.Command("alice@example.com", "bad-pw");

        assertThatThrownBy(() -> service.execute(cmd))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).appCode())
                .isEqualTo(AppCode.LOGIN_FAILED);
    }

    @Test
    void happy_path_logs_in_via_StpUtil_and_returns_token() {
        UserId id = UserId.newId();
        User user = User.reconstitute(id, TENANT_ID, "Alice", "alice@example.com", "stored-hash", 0, null, "ACTIVE");
        when(repo.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordService.verify("S3cret!", "stored-hash")).thenReturn(true);
        when(repo.recordLoginSuccess(id))
                .thenReturn(User.reconstitute(id, TENANT_ID, "Alice", "alice@example.com", "stored-hash", 0, null, "ACTIVE"));

        try (MockedStatic<StpUtil> mocked = mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getTokenValue).thenReturn("tok-abc-123");

            AuthenticateUserUseCase.Command cmd =
                    new AuthenticateUserUseCase.Command("alice@example.com", "S3cret!");

            String token = service.execute(cmd);

            assertThat(token).isEqualTo("tok-abc-123");
            // tenantId is embedded in the SaLoginModel extras — just verify login was called with the right userId
            mocked.verify(() -> StpUtil.login(eq(id.value().toString()),
                    any(cn.dev33.satoken.stp.SaLoginModel.class)));
        }
    }

    @Test
    void happy_path_does_not_leak_whether_email_or_password_was_wrong() {
        when(repo.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        UserId id = UserId.newId();
        User user = User.reconstitute(id, TENANT_ID, "Alice", "alice@example.com", "stored-hash", 0, null, "ACTIVE");
        lenient().when(repo.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        lenient().when(passwordService.verify(any(), eq("stored-hash"))).thenReturn(false);

        AppException noSuchUser = (AppException) catchException(() ->
                service.execute(new AuthenticateUserUseCase.Command("ghost@example.com", "x")));
        AppException wrongPassword = (AppException) catchException(() ->
                service.execute(new AuthenticateUserUseCase.Command("alice@example.com", "x")));

        assertThat(noSuchUser.appCode()).isEqualTo(AppCode.LOGIN_FAILED);
        assertThat(wrongPassword.appCode()).isEqualTo(AppCode.LOGIN_FAILED);
    }

    // ──────────── Wave 3: lockout + login lifecycle events ────────────

    @Test
    void unknown_email_publishes_LoginFailed_with_null_actor_and_preserves_timing() {
        when(repo.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        AuthenticateUserUseCase.Command cmd =
                new AuthenticateUserUseCase.Command("ghost@example.com", "whatever");

        assertThatThrownBy(() -> service.execute(cmd)).isInstanceOf(AppException.class);

        // Timing guard: canary-hash verify must have run (discarded result).
        // Hash arg is whatever the constructor produced from passwordService.hash(...)
        // — the Mock returns null by default; what matters is the verify call fired.
        verify(passwordService).verify(eq("whatever"), any());

        ArgumentCaptor<LoginEvent.LoginFailed> captor =
                ArgumentCaptor.forClass(LoginEvent.LoginFailed.class);
        verify(events).publishEvent(captor.capture());
        LoginEvent.LoginFailed event = captor.getValue();
        assertThat(event.targetUserId()).isNull();
        assertThat(event.reason()).isEqualTo("UNKNOWN_EMAIL");
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void rejects_when_account_currently_locked() {
        UserId id = UserId.newId();
        Instant lockedUntil = NOW.plus(Duration.ofMinutes(1));
        User locked = User.reconstitute(
                id, TENANT_ID, "Alice", "alice@example.com", "stored-hash", 0, lockedUntil, "ACTIVE");
        when(repo.findByEmail("alice@example.com")).thenReturn(Optional.of(locked));

        AuthenticateUserUseCase.Command cmd =
                new AuthenticateUserUseCase.Command("alice@example.com", "anything");

        assertThatThrownBy(() -> service.execute(cmd))
                .isInstanceOf(AccountLockedException.class);

        // MUST not mutate persistence or burn a password verify for locked users.
        verify(repo, never()).save(any());
        verify(passwordService, never()).verify(eq("anything"), any(String.class));

        ArgumentCaptor<LoginEvent.LoginFailed> captor =
                ArgumentCaptor.forClass(LoginEvent.LoginFailed.class);
        verify(events).publishEvent(captor.capture());
        LoginEvent.LoginFailed event = captor.getValue();
        assertThat(event.targetUserId()).isEqualTo(id.value());
        assertThat(event.reason()).isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    void increments_counter_on_wrong_password_below_threshold() {
        UserId id = UserId.newId();
        User user = User.reconstitute(
                id, TENANT_ID, "Alice", "alice@example.com", "stored-hash", 2, null, "ACTIVE");
        when(repo.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordService.verify("bad", "stored-hash")).thenReturn(false);
        // Gate 3 C1: service now calls repo.recordLoginFailure (atomic SQL) instead of save.
        when(repo.recordLoginFailure(eq(id), eq(NOW), anyInt(), any()))
                .thenReturn(User.reconstitute(id, TENANT_ID, "Alice", "alice@example.com", "stored-hash", 3, null, "ACTIVE"));

        assertThatThrownBy(() -> service.execute(
                new AuthenticateUserUseCase.Command("alice@example.com", "bad")))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).appCode())
                .isEqualTo(AppCode.LOGIN_FAILED);

        verify(repo).recordLoginFailure(eq(id), eq(NOW), eq(5), eq(Duration.ofMinutes(15)));
        verify(repo, never()).save(any());

        ArgumentCaptor<LoginEvent.LoginFailed> eventCaptor =
                ArgumentCaptor.forClass(LoginEvent.LoginFailed.class);
        verify(events).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().reason()).isEqualTo("WRONG_PASSWORD");
        assertThat(eventCaptor.getValue().targetUserId()).isEqualTo(id.value());
    }

    @Test
    void locks_account_on_threshold_reached() {
        // Gate 3 C1: lock-tripping logic now lives atomically in repo.recordLoginFailure
        // (tested against real Postgres in UserJooqRepositoryAdapterTest).
        UserId id = UserId.newId();
        User user = User.reconstitute(
                id, TENANT_ID, "Alice", "alice@example.com", "stored-hash", 4, null, "ACTIVE");
        when(repo.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordService.verify("bad", "stored-hash")).thenReturn(false);
        when(repo.recordLoginFailure(eq(id), eq(NOW), anyInt(), any()))
                .thenReturn(User.reconstitute(id, TENANT_ID, "Alice", "alice@example.com", "stored-hash",
                        0, NOW.plus(Duration.ofMinutes(15)), "ACTIVE"));

        assertThatThrownBy(() -> service.execute(
                new AuthenticateUserUseCase.Command("alice@example.com", "bad")))
                .isInstanceOf(AppException.class);

        verify(repo).recordLoginFailure(eq(id), eq(NOW), eq(5), eq(Duration.ofMinutes(15)));
    }

    @Test
    void resets_counter_and_publishes_LoginSucceeded_on_success() {
        UserId id = UserId.newId();
        User user = User.reconstitute(
                id, TENANT_ID, "Alice", "alice@example.com", "stored-hash", 3, null, "ACTIVE");
        when(repo.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordService.verify("S3cret!", "stored-hash")).thenReturn(true);
        when(repo.recordLoginSuccess(id))
                .thenReturn(User.reconstitute(id, TENANT_ID, "Alice", "alice@example.com", "stored-hash", 0, null, "ACTIVE"));

        try (MockedStatic<StpUtil> mocked = mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getTokenValue).thenReturn("tok-xyz");

            service.execute(new AuthenticateUserUseCase.Command("alice@example.com", "S3cret!"));
        }

        verify(repo).recordLoginSuccess(id);
        verify(repo, never()).save(any());

        ArgumentCaptor<LoginEvent.LoginSucceeded> eventCaptor =
                ArgumentCaptor.forClass(LoginEvent.LoginSucceeded.class);
        verify(events).publishEvent(eventCaptor.capture());
        LoginEvent.LoginSucceeded event = eventCaptor.getValue();
        assertThat(event.userId()).isEqualTo(id);
        assertThat(event.occurredAt()).isEqualTo(NOW);
    }

    private static Throwable catchException(Runnable r) {
        try {
            r.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }
}
