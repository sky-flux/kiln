package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.api.AuditService;
import com.skyflux.kiln.audit.domain.AuditEvent;
import com.skyflux.kiln.audit.domain.AuditEventType;
import com.skyflux.kiln.audit.repo.AuditEventJooqRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Default {@link AuditService} implementation.
 *
 * <p>{@code @Transactional(propagation = REQUIRES_NEW)} ensures the audit
 * INSERT commits independently of the caller's transaction. A failed business
 * operation must still leave a record that someone tried — that is the whole
 * point of the audit log.
 */
@Service
class AuditServiceImpl implements AuditService {

    private final Clock clock;
    private final AuditEventJooqRepository repo;

    AuditServiceImpl(Clock clock, AuditEventJooqRepository repo) {
        this.clock = clock;
        this.repo = repo;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent record(AuditEventType type,
                             UUID actorUserId,
                             UUID targetUserId,
                             String details,
                             String requestId) {
        AuditEvent event = AuditEvent.create(clock, type, actorUserId, targetUserId, details, requestId);
        repo.save(event);
        return event;
    }
}
