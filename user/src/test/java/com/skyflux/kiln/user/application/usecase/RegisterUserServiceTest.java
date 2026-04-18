package com.skyflux.kiln.user.application.usecase;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.common.security.PasswordService;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.tenant.api.TenantContext;
import com.skyflux.kiln.user.application.port.in.RegisterUserUseCase;
import com.skyflux.kiln.user.application.port.out.UserRepository;
import com.skyflux.kiln.user.domain.event.UserRegistered;
import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterUserServiceTest {

    @Mock
    UserRepository repo;

    @Mock
    ApplicationEventPublisher events;

    @Mock
    PasswordService passwordService;

    @InjectMocks
    RegisterUserService service;

    private static final UUID TENANT_ID = Ids.next();

    /** Run a block inside a bound TenantContext.CURRENT — mimics what TenantFilter does per-request. */
    private <T> T withTenant(java.util.concurrent.Callable<T> block) throws Exception {
        try {
            return ScopedValue.where(TenantContext.CURRENT, TENANT_ID)
                    .call(block::call);
        } catch (Exception e) {
            throw e;
        }
    }

    @Test
    void happy_path_hashes_password_saves_user_publishes_event_and_returns_id() throws Exception {
        when(passwordService.hash("S3cret!")).thenReturn("hashed-S3cret");

        RegisterUserUseCase.Command cmd =
                new RegisterUserUseCase.Command("Alice", "ALICE@Example.com", "S3cret!");

        UserId id = withTenant(() -> service.execute(cmd));

        assertThat(id).isNotNull();

        verify(passwordService).hash("S3cret!");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(repo).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.id()).isEqualTo(id);
        assertThat(saved.tenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.name()).isEqualTo("Alice");
        assertThat(saved.email()).isEqualTo("alice@example.com");
        assertThat(saved.passwordHash()).isEqualTo("hashed-S3cret");

        ArgumentCaptor<UserRegistered> eventCaptor = ArgumentCaptor.forClass(UserRegistered.class);
        verify(events).publishEvent(eventCaptor.capture());
        UserRegistered published = eventCaptor.getValue();
        assertThat(published.userId()).isEqualTo(id);
        assertThat(published.tenantId()).isEqualTo(TENANT_ID);
        assertThat(published.email()).isEqualTo("alice@example.com");
        assertThat(published.occurredAt()).isNotNull();
    }

    @Test
    void null_name_rejected_with_validation_failed() throws Exception {
        // lenient — if the service fails fast before calling hash, the stub is unused.
        lenient().when(passwordService.hash(any())).thenReturn("whatever");

        RegisterUserUseCase.Command cmd =
                new RegisterUserUseCase.Command(null, "a@b.com", "S3cret!");

        assertThatThrownBy(() -> withTenant(() -> service.execute(cmd)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).appCode())
                .isEqualTo(AppCode.VALIDATION_FAILED);

        verify(repo, never()).save(org.mockito.ArgumentMatchers.any());
        verify(events, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blank_name_rejected_with_validation_failed() throws Exception {
        lenient().when(passwordService.hash(any())).thenReturn("whatever");

        RegisterUserUseCase.Command cmd =
                new RegisterUserUseCase.Command("   ", "a@b.com", "S3cret!");

        assertThatThrownBy(() -> withTenant(() -> service.execute(cmd)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).appCode())
                .isEqualTo(AppCode.VALIDATION_FAILED);
    }

    @Test
    void null_email_rejected_with_validation_failed() throws Exception {
        lenient().when(passwordService.hash(any())).thenReturn("whatever");

        RegisterUserUseCase.Command cmd =
                new RegisterUserUseCase.Command("Alice", null, "S3cret!");

        assertThatThrownBy(() -> withTenant(() -> service.execute(cmd)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).appCode())
                .isEqualTo(AppCode.VALIDATION_FAILED);
    }

    @Test
    void blank_email_rejected_with_validation_failed() throws Exception {
        lenient().when(passwordService.hash(any())).thenReturn("whatever");

        RegisterUserUseCase.Command cmd =
                new RegisterUserUseCase.Command("Alice", "   ", "S3cret!");

        assertThatThrownBy(() -> withTenant(() -> service.execute(cmd)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).appCode())
                .isEqualTo(AppCode.VALIDATION_FAILED);
    }

    @Test
    void null_password_rejected_with_validation_failed() throws Exception {
        // PasswordService contract: null/blank plaintext → IllegalArgumentException.
        // Simulate that contract here — service must translate to VALIDATION_FAILED.
        when(passwordService.hash(null)).thenThrow(new IllegalArgumentException("blank"));

        RegisterUserUseCase.Command cmd =
                new RegisterUserUseCase.Command("Alice", "a@b.com", null);

        assertThatThrownBy(() -> withTenant(() -> service.execute(cmd)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).appCode())
                .isEqualTo(AppCode.VALIDATION_FAILED);

        verify(repo, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blank_password_rejected_with_validation_failed() throws Exception {
        when(passwordService.hash("   ")).thenThrow(new IllegalArgumentException("blank"));

        RegisterUserUseCase.Command cmd =
                new RegisterUserUseCase.Command("Alice", "a@b.com", "   ");

        assertThatThrownBy(() -> withTenant(() -> service.execute(cmd)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).appCode())
                .isEqualTo(AppCode.VALIDATION_FAILED);
    }

    @Test
    void hash_exception_surfaces_as_validation_failed() throws Exception {
        // Any IAE from PasswordService must be translated — it represents a bad input.
        when(passwordService.hash(any())).thenThrow(new IllegalArgumentException("invalid"));

        RegisterUserUseCase.Command cmd =
                new RegisterUserUseCase.Command("Alice", "a@b.com", "whatever");

        assertThatThrownBy(() -> withTenant(() -> service.execute(cmd)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).appCode())
                .isEqualTo(AppCode.VALIDATION_FAILED);
    }

    @Test
    void duplicate_key_translated_to_conflict() throws Exception {
        when(passwordService.hash(any())).thenReturn("hashed");
        RegisterUserUseCase.Command cmd =
                new RegisterUserUseCase.Command("Alice", "alice@example.com", "S3cret!");
        doThrow(new DuplicateKeyException("dup email")).when(repo).save(org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> withTenant(() -> service.execute(cmd)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).appCode())
                .isEqualTo(AppCode.CONFLICT);

        verify(events, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void null_command_rejected_with_NPE() throws Exception {
        assertThatNullPointerException()
                .isThrownBy(() -> withTenant(() -> service.execute(null)));
    }
}
