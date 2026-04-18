package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.domain.Audit;
import com.skyflux.kiln.audit.domain.AuditType;
import com.skyflux.kiln.audit.repo.AuditRepository;
import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuditQueryServiceImpl}. Verifies filter pass-through
 * to the repository — the service is a thin delegation layer that only adds a
 * transactional boundary.
 */
class AuditQueryServiceImplTest {

    private static final UUID ACTOR = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private final AuditRepository repo = mock(AuditRepository.class);
    private final AuditQueryServiceImpl service = new AuditQueryServiceImpl(repo);

    @Test
    void listPassesThroughFiltersAndPage() {
        PageQuery page = new PageQuery(1, 20, null);
        PageResult<Audit> expected = PageResult.of(List.of(sample()), 1L, page);
        when(repo.list(page, AuditType.LOGIN_SUCCESS, ACTOR, null)).thenReturn(expected);

        PageResult<Audit> actual = service.list(page, AuditType.LOGIN_SUCCESS, ACTOR, null);

        assertThat(actual).isSameAs(expected);
        verify(repo).list(page, AuditType.LOGIN_SUCCESS, ACTOR, null);
    }

    @Test
    void listForwardsNullFilters() {
        PageQuery page = new PageQuery(1, 20, null);
        when(repo.list(any(), isNull(), isNull(), isNull())).thenReturn(PageResult.empty(page));

        service.list(page, null, null, null);

        verify(repo).list(eq(page), isNull(), isNull(), isNull());
    }

    private static Audit sample() {
        return new Audit(
                UUID.randomUUID(),
                Instant.parse("2026-04-18T09:00:00Z"),
                AuditType.LOGIN_SUCCESS,
                ACTOR,
                null,
                null,
                null);
    }
}
