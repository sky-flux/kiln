package com.skyflux.kiln.auth.repo;

import com.skyflux.kiln.infra.jooq.generated.Tables;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * jOOQ-backed query for the transitive user → permissions lookup.
 *
 * <p>Joins {@code user_roles} → {@code role_permissions} → {@code permissions}
 * and returns distinct permission codes. A user holding multiple roles that
 * grant the same permission still sees it once, which matches Sa-Token's
 * expectation that {@code @SaCheckPermission("x")} is a set membership test.
 */
@Repository
public class PermissionJooqRepository {

    private final DSLContext dsl;

    PermissionJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<String> findCodesByUserId(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        return dsl.selectDistinct(Tables.PERMISSIONS.CODE)
                .from(Tables.USER_ROLES)
                .join(Tables.ROLE_PERMISSIONS)
                        .on(Tables.ROLE_PERMISSIONS.ROLE_ID.eq(Tables.USER_ROLES.ROLE_ID))
                .join(Tables.PERMISSIONS)
                        .on(Tables.PERMISSIONS.ID.eq(Tables.ROLE_PERMISSIONS.PERMISSION_ID))
                .where(Tables.USER_ROLES.USER_ID.eq(userId))
                .fetch(Tables.PERMISSIONS.CODE);
    }
}
