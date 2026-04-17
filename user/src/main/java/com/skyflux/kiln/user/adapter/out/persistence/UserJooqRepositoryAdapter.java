package com.skyflux.kiln.user.adapter.out.persistence;

import com.skyflux.kiln.infra.jooq.generated.Tables;
import com.skyflux.kiln.infra.jooq.generated.tables.records.UsersRecord;
import com.skyflux.kiln.user.application.port.out.UserRepository;
import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

/**
 * jOOQ-backed out-adapter implementing {@link UserRepository}.
 *
 * <p>Phase 3 Wave 2 replaces the in-memory seed adapter. Save is an UPSERT so
 * the repository stays idempotent for both {@code register} (new aggregate) and
 * {@code reconstitute} (loaded + mutated + saved) flows. {@code created_at}
 * stays stable across updates because the {@code DO UPDATE} clause only
 * touches mutable columns.
 */
@Repository
class UserJooqRepositoryAdapter implements UserRepository {

    private final DSLContext dsl;
    private final UserMapper mapper;

    UserJooqRepositoryAdapter(DSLContext dsl, UserMapper mapper) {
        this.dsl = dsl;
        this.mapper = mapper;
    }

    @Override
    public Optional<User> findById(UserId id) {
        Objects.requireNonNull(id, "id");
        return dsl.selectFrom(Tables.USERS)
                .where(Tables.USERS.ID.eq(id.value()))
                .fetchOptional()
                .map(mapper::toAggregate);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        Objects.requireNonNull(email, "email");
        return dsl.selectFrom(Tables.USERS)
                .where(Tables.USERS.EMAIL.eq(email))
                .fetchOptional()
                .map(mapper::toAggregate);
    }

    @Override
    public void save(User user) {
        Objects.requireNonNull(user, "user");
        UsersRecord r = mapper.toRecord(user);
        dsl.insertInto(Tables.USERS)
                .set(r)
                .onConflict(Tables.USERS.ID)
                .doUpdate()
                // ────────────────────────────────────────────────────────────────
                // INVARIANT: do NOT include Tables.USERS.CREATED_AT here.
                // The UPSERT DO UPDATE path intentionally preserves the original
                // insert timestamp. Adding `.set(CREATED_AT, r.getCreatedAt())`
                // would corrupt audit history on every subsequent save.
                // Enforced by UserJooqRepositoryAdapterTest.upsertPreservesCreatedAt
                // and UserMapperTest.updatePayloadDoesNotLeakCreatedAt.
                // ────────────────────────────────────────────────────────────────
                .set(Tables.USERS.NAME, r.getName())
                .set(Tables.USERS.EMAIL, r.getEmail())
                .set(Tables.USERS.UPDATED_AT, r.getUpdatedAt())
                .execute();
    }
}
