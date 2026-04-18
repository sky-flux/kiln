package com.skyflux.kiln.auth.repo;

import com.skyflux.kiln.auth.domain.Role;
import com.skyflux.kiln.infra.jooq.generated.Tables;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * jOOQ-backed repository for the {@code roles} catalogue.
 *
 * <p>Public so it can be wired into {@code auth.internal} services — but
 * consumed only inside this module. Downstream modules go through
 * {@link com.skyflux.kiln.auth.api.RoleAssignmentService} instead. The
 * module boundary is enforced by Spring Modulith's {@code named-interface}
 * semantics (see {@code auth/package-info.java}).
 */
@Repository
public class RoleJooqRepository {

    private final DSLContext dsl;

    RoleJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<Role> findByCode(String code) {
        Objects.requireNonNull(code, "code");
        return dsl.selectFrom(Tables.ROLES)
                .where(Tables.ROLES.CODE.eq(code))
                .fetchOptional()
                .map(r -> new Role(r.getId(), r.getCode(), r.getName(), r.getTenantId()));
    }

    public Optional<Role> findById(UUID id) {
        Objects.requireNonNull(id, "id");
        return dsl.selectFrom(Tables.ROLES)
                .where(Tables.ROLES.ID.eq(id))
                .fetchOptional()
                .map(r -> new Role(r.getId(), r.getCode(), r.getName(), r.getTenantId()));
    }

    public void save(Role role) {
        Objects.requireNonNull(role, "role");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.insertInto(Tables.ROLES)
                .set(Tables.ROLES.ID, role.id())
                .set(Tables.ROLES.CODE, role.code())
                .set(Tables.ROLES.NAME, role.name())
                .set(Tables.ROLES.TENANT_ID, role.tenantId())
                .set(Tables.ROLES.CREATED_AT, now)
                .onConflict(Tables.ROLES.ID)
                .doUpdate()
                .set(Tables.ROLES.NAME, role.name())
                .execute();
    }

    public void delete(UUID roleId) {
        Objects.requireNonNull(roleId, "roleId");
        dsl.deleteFrom(Tables.ROLES).where(Tables.ROLES.ID.eq(roleId)).execute();
    }

    public List<Role> listAll() {
        return dsl.selectFrom(Tables.ROLES)
                .orderBy(Tables.ROLES.CODE)
                .fetch()
                .map(r -> new Role(r.getId(), r.getCode(), r.getName(), r.getTenantId()));
    }
}
