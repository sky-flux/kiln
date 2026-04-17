package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.api.AuditQueryService;
import com.skyflux.kiln.audit.domain.AuditEvent;
import com.skyflux.kiln.audit.domain.AuditEventType;
import com.skyflux.kiln.audit.repo.AuditEventJooqRepository;
import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Default {@link AuditQueryService} — delegates read-only filtering + paging
 * to the repository. Sits behind {@code @Transactional(readOnly = true)} so
 * the jOOQ queries share one short-lived read transaction.
 */
@Service
class AuditQueryServiceImpl implements AuditQueryService {

    private final AuditEventJooqRepository repo;

    AuditQueryServiceImpl(AuditEventJooqRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AuditEvent> list(PageQuery page, AuditEventType type, UUID actorUserId, UUID targetUserId) {
        return repo.list(page, type, actorUserId, targetUserId);
    }
}
