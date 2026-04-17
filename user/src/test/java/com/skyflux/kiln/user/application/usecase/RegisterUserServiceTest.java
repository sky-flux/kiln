package com.skyflux.kiln.user.application.usecase;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegisterUserServiceTest {

    @Mock
    UserRepository repo;

    @Mock
    ApplicationEventPublisher events;

    @InjectMocks
    RegisterUserService service;

    @Test
    void happy_path_saves_user_publishes_event_and_returns_id() {
        RegisterUserUseCase.Command cmd =
                new RegisterUserUseCase.Command("Alice", "ALICE@Example.com");

        UserId id = service.execute(cmd);

        assertThat(id).isNotNull();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(repo).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.id()).isEqualTo(id);
        assertThat(saved.name()).isEqualTo("Alice");
        assertThat(saved.email()).isEqualTo("alice@example.com");

        ArgumentCaptor<UserRegistered> eventCaptor = ArgumentCaptor.forClass(UserRegistered.class);
        verify(events).publishEvent(eventCaptor.capture());
        UserRegistered published = eventCaptor.getValue();
        assertThat(published.userId()).isEqualTo(id);
        assertThat(published.email()).isEqualTo("alice@example.com");
        assertThat(published.occurredAt()).isNotNull();
    }

    @Test
    void null_name_rejected_with_validation_failed() {
        RegisterUserUseCase.Command cmd =
                new RegisterUserUseCase.Command(null, "a@b.com");

        assertThatThrownBy(() -> service.execute(cmd))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).appCode())
                .isEqualTo(AppCode.VALIDATION_FAILED);

        verify(repo, never()).save(org.mockito.ArgumentMatchers.any());
        verify(events, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blank_name_rejected_with_validation_failed() {
        RegisterUserUseCase.Command cmd =
                new RegisterUserUseCase.Command("   ", "a@b.com");

        assertThatThrownBy(() -> service.execute(cmd))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).appCode())
                .isEqualTo(AppCode.VALIDATION_FAILED);
    }

    @Test
    void null_email_rejected_with_validation_failed() {
        RegisterUserUseCase.Command cmd =
                new RegisterUserUseCase.Command("Alice", null);

        assertThatThrownBy(() -> service.execute(cmd))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).appCode())
                .isEqualTo(AppCode.VALIDATION_FAILED);
    }

    @Test
    void blank_email_rejected_with_validation_failed() {
        RegisterUserUseCase.Command cmd =
                new RegisterUserUseCase.Command("Alice", "   ");

        assertThatThrownBy(() -> service.execute(cmd))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).appCode())
                .isEqualTo(AppCode.VALIDATION_FAILED);
    }

    @Test
    void duplicate_key_translated_to_conflict() {
        RegisterUserUseCase.Command cmd =
                new RegisterUserUseCase.Command("Alice", "alice@example.com");
        doThrow(new DuplicateKeyException("dup email")).when(repo).save(org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> service.execute(cmd))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).appCode())
                .isEqualTo(AppCode.CONFLICT);

        verify(events, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void null_command_rejected_with_NPE() {
        assertThatNullPointerException()
                .isThrownBy(() -> service.execute(null));
    }
}
