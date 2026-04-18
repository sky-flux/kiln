package com.skyflux.kiln.user.adapter.out.persistence;

import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.infra.jooq.generated.Tables;
import com.skyflux.kiln.infra.jooq.generated.tables.records.UsersRecord;
import com.skyflux.kiln.user.application.port.out.UserRepository;
import com.skyflux.kiln.user.domain.model.User;
import com.skyflux.kiln.user.domain.model.UserId;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
    public long countAll() {
        Long n = dsl.selectCount().from(Tables.USERS).fetchOne(0, Long.class);
        return n == null ? 0L : n;
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
                //
                // Phase 4.3 Wave 1: lockout bookkeeping columns must also round-trip.
                // Wave 2's lockout service loads the aggregate, mutates the counter /
                // locked_until, and calls save(); the DO UPDATE therefore writes ALL
                // domain-mutable columns (name, password_hash, failed_login_attempts,
                // locked_until) so the mutated state lands. Enforced by
                // UserJooqRepositoryAdapterTest.saveUpdatesLockoutFieldsOnUpsert.
                // ────────────────────────────────────────────────────────────────
                .set(Tables.USERS.TENANT_ID, r.getTenantId())
                .set(Tables.USERS.NAME, r.getName())
                .set(Tables.USERS.EMAIL, r.getEmail())
                .set(Tables.USERS.PASSWORD_HASH, r.getPasswordHash())
                .set(Tables.USERS.FAILED_LOGIN_ATTEMPTS, r.getFailedLoginAttempts())
                .set(Tables.USERS.LOCKED_UNTIL, r.getLockedUntil())
                .set(Tables.USERS.STATUS, r.getStatus())
                .set(Tables.USERS.UPDATED_AT, r.getUpdatedAt())
                .execute();
    }

    @Override
    public User recordLoginFailure(UserId id, Instant now, int threshold, Duration duration) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(duration, "duration");
        if (threshold < 1) {
            throw new IllegalArgumentException("threshold must be >= 1");
        }
        // Gate 3 C1: single atomic UPDATE eliminates the lost-update race that
        // plagued the previous read-modify-write save(). CASE expressions mirror
        // User.registerLoginFailure's rule — post-increment >= threshold locks
        // and resets counter; otherwise just increments. RETURNING * gives us
        // the post-UPDATE state for event publication.
        var nextCount = Tables.USERS.FAILED_LOGIN_ATTEMPTS.plus(1);
        var willLock = nextCount.ge(threshold);
        var lockUntil = now.plus(duration).atOffset(ZoneOffset.UTC);

        UsersRecord r = dsl.update(Tables.USERS)
                .set(Tables.USERS.FAILED_LOGIN_ATTEMPTS,
                        DSL.when(willLock, 0).otherwise(nextCount))
                .set(Tables.USERS.LOCKED_UNTIL,
                        DSL.when(willLock, lockUntil).otherwise(Tables.USERS.LOCKED_UNTIL))
                .set(Tables.USERS.UPDATED_AT, now.atOffset(ZoneOffset.UTC))
                .where(Tables.USERS.ID.eq(id.value()))
                .returning()
                .fetchOne();
        if (r == null) {
            throw new IllegalStateException("recordLoginFailure: user not found " + id);
        }
        return mapper.toAggregate(r);
    }

    @Override
    public User recordLoginSuccess(UserId id) {
        Objects.requireNonNull(id, "id");
        UsersRecord r = dsl.update(Tables.USERS)
                .set(Tables.USERS.FAILED_LOGIN_ATTEMPTS, 0)
                .setNull(Tables.USERS.LOCKED_UNTIL)
                .where(Tables.USERS.ID.eq(id.value()))
                .returning()
                .fetchOne();
        if (r == null) {
            throw new IllegalStateException("recordLoginSuccess: user not found " + id);
        }
        return mapper.toAggregate(r);
    }

    @Override
    public PageResult<User> listActive(PageQuery query) {
        Objects.requireNonNull(query, "query");
        var cond = Tables.USERS.STATUS.eq("ACTIVE");
        long total = Optional.ofNullable(
                dsl.selectCount().from(Tables.USERS).where(cond).fetchOne(0, Long.class)
        ).orElse(0L);
        List<User> items = dsl.selectFrom(Tables.USERS)
                .where(cond)
                .orderBy(Tables.USERS.CREATED_AT.desc())
                .limit(query.size()).offset(query.offset())
                .fetch()
                .map(mapper::toAggregate);
        return PageResult.of(items, total, query);
    }
}
