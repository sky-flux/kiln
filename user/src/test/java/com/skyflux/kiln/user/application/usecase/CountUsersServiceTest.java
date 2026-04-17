package com.skyflux.kiln.user.application.usecase;

import com.skyflux.kiln.user.application.port.in.CountUsersUseCase;
import com.skyflux.kiln.user.application.port.out.UserRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Phase 4.2 Gate 3 I3 fix — admin endpoints must go through
 * an inbound use-case port, not wire directly to {@code UserRepository}
 * (outbound port). This service is thin; the test simply pins delegation so
 * a future refactor can't quietly elide the use-case layer.
 */
class CountUsersServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final CountUsersUseCase useCase = new CountUsersService(users);

    @Test
    void executeDelegatesToCountAll() {
        when(users.countAll()).thenReturn(42L);

        assertThat(useCase.execute()).isEqualTo(42L);
    }

    @Test
    void executeReturnsZeroWhenRepoEmpty() {
        when(users.countAll()).thenReturn(0L);

        assertThat(useCase.execute()).isZero();
    }
}
