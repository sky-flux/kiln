package com.skyflux.kiln.auth.repo;

import com.skyflux.kiln.infra.jooq.generated.Tables;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * jOOQ-backed repository for the {@code user_roles} join table.
 *
 * <p>{@link #assign} uses {@code INSERT ... ON CONFLICT DO NOTHING} so that
 * repeated {@code UserRegistered} events (or idempotent retries) never
 * produce duplicate rows or blow up on the composite primary key. Relying on
 * PostgreSQL's native UPSERT keeps the contract server-side — a client-side
 * "exists then insert" would race under concurrent event listeners.
 */
@Repository
public class UserRoleJooqRepository {

    private final DSLContext dsl;

    UserRoleJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void assign(UUID userId, UUID roleId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(roleId, "roleId");
        dsl.insertInto(Tables.USER_ROLES)
                .set(Tables.USER_ROLES.USER_ID, userId)
                .set(Tables.USER_ROLES.ROLE_ID, roleId)
                .onConflict(Tables.USER_ROLES.USER_ID, Tables.USER_ROLES.ROLE_ID)
                .doNothing()
                .execute();
    }

    /**
     * Remove a single {@code (user_id, role_id)} assignment. Idempotent — if
     * no row matches, the DELETE affects 0 rows and returns normally.
     */
    public void revoke(UUID userId, UUID roleId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(roleId, "roleId");
        dsl.deleteFrom(Tables.USER_ROLES)
                .where(Tables.USER_ROLES.USER_ID.eq(userId))
                .and(Tables.USER_ROLES.ROLE_ID.eq(roleId))
                .execute();
    }

    public List<String> findRoleCodesByUserId(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        return dsl.select(Tables.ROLES.CODE)
                .from(Tables.USER_ROLES)
                .join(Tables.ROLES).on(Tables.ROLES.ID.eq(Tables.USER_ROLES.ROLE_ID))
                .where(Tables.USER_ROLES.USER_ID.eq(userId))
                .fetch(Tables.ROLES.CODE);
    }
}
