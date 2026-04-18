package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.api.AuditService;
import com.skyflux.kiln.audit.domain.Audit;
import com.skyflux.kiln.audit.domain.AuditAction;
import com.skyflux.kiln.audit.domain.AuditResource;
import com.skyflux.kiln.audit.repo.AuditRepository;
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
    private final AuditRepository repo;

    AuditServiceImpl(Clock clock, AuditRepository repo) {
        this.clock = clock;
        this.repo = repo;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Audit record(AuditResource resource,
                        AuditAction action,
                        UUID actorUserId,
                        UUID targetUserId,
                        String details,
                        String requestId) {
        Audit event = Audit.create(clock, resource, action, actorUserId, targetUserId, details, requestId);
        repo.save(event);
        return event;
    }
}
