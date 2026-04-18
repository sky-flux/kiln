package com.skyflux.kiln.user.application.usecase;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.tenant.api.TenantContext;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteUserServiceTest {

    @Mock
    UserRepository repo;

    @InjectMocks
    DeleteUserService service;

    @Test
    void shouldDeactivateUserOnDelete() {
        UserId id = UserId.newId();
        User active = User.reconstitute(id, Ids.next(), "Alice", "a@b.com", "h", 0, null, "ACTIVE");
        when(repo.findById(id)).thenReturn(Optional.of(active));

        service.execute(id);

        verify(repo).save(argThat(u -> "INACTIVE".equals(u.status())));
    }

    @Test
    void shouldThrowNotFoundWhenUserDoesNotExist() {
        UserId id = UserId.newId();
        when(repo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(id))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).appCode())
                .isEqualTo(AppCode.NOT_FOUND);
    }

    @Test
    void shouldRejectNullId() {
        org.assertj.core.api.Assertions.assertThatNullPointerException()
                .isThrownBy(() -> service.execute(null));
    }

    @Test
    void shouldThrowNotFoundWhenTenantContextDoesNotMatchUserTenant() {
        UUID userTenantId = Ids.next();
        UUID otherTenantId = Ids.next();
        UserId id = UserId.newId();
        User active = User.reconstitute(id, userTenantId, "Alice", "a@b.com", "h", 0, null, "ACTIVE");
        when(repo.findById(id)).thenReturn(Optional.of(active));

        assertThatThrownBy(() ->
                ScopedValue.where(TenantContext.CURRENT, otherTenantId).run(() ->
                        service.execute(id)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).appCode())
                .isEqualTo(AppCode.NOT_FOUND);
    }
}
