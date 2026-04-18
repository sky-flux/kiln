package com.skyflux.kiln.user.adapter.out.persistence;

import com.skyflux.kiln.infra.jooq.generated.tables.records.UsersRecord;
import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Translates between the {@link User} aggregate and the jOOQ-generated
 * {@link UsersRecord}.
 *
 * <p>Audit timestamps ({@code created_at}, {@code updated_at}) are NOT part of
 * the domain model today — they are synthesized at write time (UTC) and
 * discarded on read. When the aggregate eventually exposes audit times, this
 * mapper should be the first class to update.
 *
 * <p>Phase 4.3 Wave 1 adds {@code failed_login_attempts} / {@code locked_until}
 * round-trip. {@code locked_until} is nullable on both sides: {@code null}
 * means "not locked". The DB column is {@code TIMESTAMPTZ}; jOOQ surfaces it
 * as {@link OffsetDateTime}, which converts losslessly to/from {@link Instant}.
 *
 * <p>Wave 1 T8 adds {@code tenant_id} round-trip. Every user belongs to exactly
 * one tenant; the column is NOT NULL in the DB schema.
 */
@Component
class UserMapper {

    /** jOOQ record (DB row) → domain aggregate. Uses {@code reconstitute}, never {@code register}. */
    User toAggregate(UsersRecord record) {
        Integer failed = record.getFailedLoginAttempts();
        OffsetDateTime lockedUntil = record.getLockedUntil();
        String status = record.getStatus();
        return User.reconstitute(
                new UserId(record.getId()),
                record.getTenantId(),
                record.getName(),
                record.getEmail(),
                record.getPasswordHash(),
                failed == null ? 0 : failed,
                lockedUntil == null ? null : lockedUntil.toInstant(),
                status == null ? "ACTIVE" : status);
    }

    /**
     * Domain aggregate → jOOQ record ready for INSERT/UPSERT.
     *
     * <p>{@code created_at} / {@code updated_at} are both set to "now" in UTC.
     * For pure updates, the DB retains the original {@code created_at} value
     * because the UPSERT path in {@code UserJooqRepositoryAdapter} does not
     * overwrite that column.
     */
    UsersRecord toRecord(User user) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UsersRecord record = new UsersRecord();
        record.setId(user.id().value());
        record.setTenantId(user.tenantId());
        record.setName(user.name());
        record.setEmail(user.email());
        record.setPasswordHash(user.passwordHash());
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setFailedLoginAttempts(user.failedLoginAttempts());
        Instant lockedUntil = user.lockedUntil();
        record.setLockedUntil(lockedUntil == null ? null : lockedUntil.atOffset(ZoneOffset.UTC));
        record.setStatus(user.status());
        return record;
    }
}
