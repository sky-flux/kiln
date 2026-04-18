package com.skyflux.kiln.member.internal;

import cn.dev33.satoken.stp.StpUtil;
import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.member.domain.Member;
import com.skyflux.kiln.member.repo.MemberJooqRepository;
import com.skyflux.kiln.tenant.api.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
class MemberService {

    private final MemberJooqRepository repo;

    MemberService(MemberJooqRepository repo) {
        this.repo = repo;
    }

    public Member getMyMember() {
        UUID userId = UUID.fromString(StpUtil.getLoginIdAsString());
        return repo.findByUserId(userId)
                .orElseThrow(() -> new AppException(AppCode.NOT_FOUND));
    }

    public Member getByUserId(UUID userId) {
        return repo.findByUserId(userId)
                .orElseThrow(() -> new AppException(AppCode.NOT_FOUND));
    }

    @Transactional
    public Member awardPoints(UUID userId, int points) {
        Member m = getByUserId(userId);
        Member updated = m.awardPoints(points);
        repo.save(updated);
        return updated;
    }

    public PageResult<Member> list(PageQuery query) {
        UUID tenantId = TenantContext.CURRENT.get();
        return repo.listByTenant(tenantId, query);
    }
}
