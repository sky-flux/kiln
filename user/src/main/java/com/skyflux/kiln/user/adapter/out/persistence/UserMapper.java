package com.skyflux.kiln.user.adapter.out.persistence;

import com.skyflux.kiln.infra.jooq.generated.tables.records.UsersRecord;
import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;
import org.springframework.stereotype.Component;

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
 */
@Component
class UserMapper {

    /** jOOQ record (DB row) → domain aggregate. Uses {@code reconstitute}, never {@code register}. */
    User toAggregate(UsersRecord record) {
        return User.reconstitute(
                new UserId(record.getId()),
                record.getName(),
                record.getEmail(),
                record.getPasswordHash());
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
        record.setName(user.name());
        record.setEmail(user.email());
        record.setPasswordHash(user.passwordHash());
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }
}
