package com.skyflux.kiln.tenant.repo;

import com.skyflux.kiln.infra.jooq.generated.Tables;
import com.skyflux.kiln.infra.jooq.generated.tables.records.TenantsRecord;
import com.skyflux.kiln.tenant.api.TenantId;
import com.skyflux.kiln.tenant.domain.Tenant;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

@Repository
public class TenantJooqRepository {

    private final DSLContext dsl;

    TenantJooqRepository(DSLContext dsl) { this.dsl = dsl; }

    public Optional<Tenant> findById(TenantId id) {
        Objects.requireNonNull(id, "id");
        return dsl.selectFrom(Tables.TENANTS)
                .where(Tables.TENANTS.ID.eq(id.value()))
                .fetchOptional()
                .map(this::toTenant);
    }

    /** Auth-path lookup — tenants table has no RLS, safe without tenant context. */
    public Optional<Tenant> findByCode(String code) {
        Objects.requireNonNull(code, "code");
        return dsl.selectFrom(Tables.TENANTS)
                .where(Tables.TENANTS.CODE.eq(code))
                .fetchOptional()
                .map(this::toTenant);
    }

    public void save(Tenant tenant) {
        Objects.requireNonNull(tenant, "tenant");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.insertInto(Tables.TENANTS)
                .set(Tables.TENANTS.ID, tenant.id().value())
                .set(Tables.TENANTS.CODE, tenant.code())
                .set(Tables.TENANTS.NAME, tenant.name())
                .set(Tables.TENANTS.STATUS, tenant.status())
                .set(Tables.TENANTS.CREATED_AT, now)
                .set(Tables.TENANTS.UPDATED_AT, now)
                .onConflict(Tables.TENANTS.ID)
                .doUpdate()
                .set(Tables.TENANTS.NAME, tenant.name())
                .set(Tables.TENANTS.STATUS, tenant.status())
                .set(Tables.TENANTS.UPDATED_AT, now)
                .execute();
    }

    private Tenant toTenant(TenantsRecord r) {
        return new Tenant(
                new TenantId(r.getId()),
                r.getCode(),
                r.getName(),
                r.getStatus(),
                r.getCreatedAt().toInstant());
    }
}
