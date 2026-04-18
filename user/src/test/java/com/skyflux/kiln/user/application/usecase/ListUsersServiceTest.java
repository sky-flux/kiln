package com.skyflux.kiln.user.application.usecase;

import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.user.application.port.in.ListUsersUseCase;
import com.skyflux.kiln.user.application.port.out.UserRepository;
import com.skyflux.kiln.user.domain.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListUsersServiceTest {

    @Mock
    UserRepository repo;

    @InjectMocks
    ListUsersService service;

    @Test
    void shouldDelegateToRepositoryAndReturnResult() {
        UUID tenantId = Ids.next();
        PageQuery query = new PageQuery(1, 10, null);
        User u = User.register(tenantId, "Alice", "alice@example.com", "hash");
        PageResult<User> expected = PageResult.of(List.of(u), 1L, query);
        when(repo.listActive(query)).thenReturn(expected);

        PageResult<User> result = service.execute(query);

        assertThat(result).isSameAs(expected);
        verify(repo).listActive(query);
    }

    @Test
    void shouldRejectNullQuery() {
        org.assertj.core.api.Assertions.assertThatNullPointerException()
                .isThrownBy(() -> service.execute(null));
    }
}
