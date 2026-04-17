package com.skyflux.kiln.user.application.usecase;

import cn.dev33.satoken.stp.StpUtil;
import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.common.security.PasswordService;
import com.skyflux.kiln.user.application.port.in.AuthenticateUserUseCase;
import com.skyflux.kiln.user.application.port.out.UserRepository;
import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticateUserServiceTest {

    @Mock
    UserRepository repo;

    @Mock
    PasswordService passwordService;

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
        // The DB stores emails lowercased (per User.register normalization).
        // The login flow MUST normalize the query key so mixed-case logins still match.
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
        User user = User.reconstitute(id, "Alice", "alice@example.com", "stored-hash");
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
        User user = User.reconstitute(id, "Alice", "alice@example.com", "stored-hash");
        when(repo.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordService.verify("S3cret!", "stored-hash")).thenReturn(true);

        try (MockedStatic<StpUtil> mocked = mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getTokenValue).thenReturn("tok-abc-123");

            AuthenticateUserUseCase.Command cmd =
                    new AuthenticateUserUseCase.Command("alice@example.com", "S3cret!");

            String token = service.execute(cmd);

            assertThat(token).isEqualTo("tok-abc-123");
            mocked.verify(() -> StpUtil.login(id.value().toString()));
        }
    }

    @Test
    void happy_path_does_not_leak_whether_email_or_password_was_wrong() {
        // Both failure modes (no such user / wrong password) must yield the SAME error code,
        // so an attacker can't enumerate valid emails via login response codes.
        when(repo.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        UserId id = UserId.newId();
        User user = User.reconstitute(id, "Alice", "alice@example.com", "stored-hash");
        lenient().when(repo.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        lenient().when(passwordService.verify(any(), eq("stored-hash"))).thenReturn(false);

        AppException noSuchUser = (AppException) catchException(() ->
                service.execute(new AuthenticateUserUseCase.Command("ghost@example.com", "x")));
        AppException wrongPassword = (AppException) catchException(() ->
                service.execute(new AuthenticateUserUseCase.Command("alice@example.com", "x")));

        assertThat(noSuchUser.appCode()).isEqualTo(AppCode.LOGIN_FAILED);
        assertThat(wrongPassword.appCode()).isEqualTo(AppCode.LOGIN_FAILED);
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
