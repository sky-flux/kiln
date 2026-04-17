package com.skyflux.kiln.auth.repo;

import com.skyflux.kiln.auth.domain.Role;
import com.skyflux.kiln.infra.jooq.generated.Tables;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

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
                .map(r -> new Role(r.getId(), r.getCode(), r.getName()));
    }

    public Optional<Role> findById(UUID id) {
        Objects.requireNonNull(id, "id");
        return dsl.selectFrom(Tables.ROLES)
                .where(Tables.ROLES.ID.eq(id))
                .fetchOptional()
                .map(r -> new Role(r.getId(), r.getCode(), r.getName()));
    }
}
