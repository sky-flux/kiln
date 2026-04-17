package com.skyflux.kiln.user.application.usecase;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.user.application.port.out.UserRepository;
import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUserServiceTest {

    @Mock
    UserRepository repo;

    @InjectMocks
    GetUserService service;

    @Test
    void returns_user_when_repository_finds_it() {
        UserId id = UserId.newId();
        User stored = User.reconstitute(id, "Alice", "alice@example.com", "fake-hash", 0, null);
        when(repo.findById(id)).thenReturn(Optional.of(stored));

        User result = service.execute(id);

        assertThat(result).isSameAs(stored);
    }

    @Test
    void rejects_null_id_with_NPE() {
        org.assertj.core.api.Assertions
                .assertThatNullPointerException()
                .isThrownBy(() -> service.execute(null));
    }

    @Test
    void throws_AppException_with_NOT_FOUND_when_repository_is_empty() {
        UserId id = UserId.newId();
        when(repo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(id))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).appCode())
                .isEqualTo(AppCode.NOT_FOUND);
    }
}
