package com.skyflux.kiln.member.internal;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.member.domain.Member;
import com.skyflux.kiln.member.domain.MemberId;
import com.skyflux.kiln.member.domain.MemberLevel;
import com.skyflux.kiln.member.repo.MemberJooqRepository;
import com.skyflux.kiln.tenant.api.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock MemberJooqRepository repo;
    @InjectMocks MemberService service;

    @Test
    void shouldAwardPointsAndSave() {
        UUID userId = Ids.next();
        UUID tenantId = Ids.next();
        Member m = Member.reconstitute(MemberId.newId(), tenantId, userId, MemberLevel.BRONZE, 0, "ACTIVE");
        when(repo.findByUserId(userId)).thenReturn(Optional.of(m));

        Member result = service.awardPoints(userId, 500);

        assertThat(result.points()).isEqualTo(500);
        verify(repo).save(argThat(saved -> saved.points() == 500));
    }

    @Test
    void shouldThrowNotFoundForUnknownUser() {
        UUID userId = Ids.next();
        when(repo.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.awardPoints(userId, 100))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).appCode())
                .isEqualTo(AppCode.NOT_FOUND);
    }

    @Test
    void shouldReturnMemberForKnownUserId() {
        UUID userId = Ids.next();
        UUID tenantId = Ids.next();
        Member m = Member.reconstitute(MemberId.newId(), tenantId, userId, MemberLevel.BRONZE, 0, "ACTIVE");
        when(repo.findByUserId(userId)).thenReturn(Optional.of(m));

        Member result = service.getByUserId(userId);

        assertThat(result.userId()).isEqualTo(userId);
    }

    @Test
    void shouldListMembersForCurrentTenant() throws Exception {
        UUID tenantId = Ids.next();
        UUID userId = Ids.next();
        Member m = Member.reconstitute(MemberId.newId(), tenantId, userId, MemberLevel.BRONZE, 0, "ACTIVE");
        PageQuery query = new PageQuery(1, 20, null);
        when(repo.listByTenant(eq(tenantId), any(PageQuery.class)))
                .thenReturn(PageResult.of(List.of(m), 1L, query));

        PageResult<Member> result = ScopedValue.where(TenantContext.CURRENT, tenantId)
                .call(() -> service.list(query));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
    }
}
