package com.skyflux.kiln.user.application.usecase;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.tenant.api.TenantContext;
import com.skyflux.kiln.user.application.port.in.UpdateUserUseCase;
import com.skyflux.kiln.user.application.port.out.UserRepository;
import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateUserServiceTest {

    @Mock
    UserRepository repo;

    @InjectMocks
    UpdateUserService service;

    @Test
    void shouldUpdateUserName() {
        UserId id = UserId.newId();
        UUID tid = Ids.next();
        User existing = User.reconstitute(id, tid, "Old", "u@e.com", "h", 0, null, "ACTIVE");
        when(repo.findById(id)).thenReturn(Optional.of(existing));

        User result = service.execute(new UpdateUserUseCase.Command(id, "New Name"));

        assertThat(result.name()).isEqualTo("New Name");
        verify(repo).save(argThat(u -> "New Name".equals(u.name())));
    }

    @Test
    void shouldThrowNotFoundForUnknown() {
        UserId id = UserId.newId();
        when(repo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(new UpdateUserUseCase.Command(id, "X")))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).appCode())
                .isEqualTo(AppCode.NOT_FOUND);
    }

    @Test
    void shouldRejectNullCommand() {
        org.assertj.core.api.Assertions.assertThatNullPointerException()
                .isThrownBy(() -> service.execute(null));
    }

    @Test
    void shouldThrowNotFoundWhenTenantContextDoesNotMatchUserTenant() {
        UUID userTenantId = Ids.next();
        UUID otherTenantId = Ids.next();
        UserId id = UserId.newId();
        User existing = User.reconstitute(id, userTenantId, "Old", "u@e.com", "h", 0, null, "ACTIVE");
        when(repo.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
                ScopedValue.where(TenantContext.CURRENT, otherTenantId).run(() ->
                        service.execute(new UpdateUserUseCase.Command(id, "New"))))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).appCode())
                .isEqualTo(AppCode.NOT_FOUND);
    }
}
