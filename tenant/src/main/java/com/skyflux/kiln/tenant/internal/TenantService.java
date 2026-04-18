package com.skyflux.kiln.tenant.internal;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.tenant.api.TenantId;
import com.skyflux.kiln.tenant.domain.Tenant;
import com.skyflux.kiln.tenant.repo.TenantJooqRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
class TenantService {

    private final TenantJooqRepository repo;

    TenantService(TenantJooqRepository repo) { this.repo = repo; }

    @Transactional
    public Tenant create(String code, String name) {
        Objects.requireNonNull(code); Objects.requireNonNull(name);
        if (repo.findByCode(code).isPresent()) {
            throw new AppException(AppCode.CONFLICT, "Tenant code already exists: " + code);
        }
        Tenant t = new Tenant(TenantId.newId(), code, name, "ACTIVE", null);
        try {
            repo.save(t);
        } catch (DuplicateKeyException e) {
            // Concurrent creation: both threads passed the findByCode check, but the
            // DB UNIQUE(code) constraint caught the second writer.
            throw new AppException(AppCode.CONFLICT, "Tenant code already exists: " + code);
        }
        return t;
    }

    @Transactional
    public Tenant update(TenantId id, String name, String status) {
        Tenant existing = repo.findById(id)
            .orElseThrow(() -> new AppException(AppCode.NOT_FOUND));
        Tenant updated = new Tenant(id, existing.code(), name, status, existing.createdAt());
        repo.save(updated);
        return updated;
    }

    @Transactional
    public void suspend(TenantId id) {
        Tenant existing = repo.findById(id)
            .orElseThrow(() -> new AppException(AppCode.NOT_FOUND));
        repo.save(new Tenant(id, existing.code(), existing.name(), "SUSPENDED", existing.createdAt()));
    }

    public Tenant get(TenantId id) {
        return repo.findById(id).orElseThrow(() -> new AppException(AppCode.NOT_FOUND));
    }
}
