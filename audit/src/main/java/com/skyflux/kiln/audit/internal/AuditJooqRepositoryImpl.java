package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.domain.Audit;
import com.skyflux.kiln.audit.domain.AuditType;
import com.skyflux.kiln.audit.repo.AuditRepository;
import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.infra.jooq.generated.Tables;
import com.skyflux.kiln.infra.jooq.generated.tables.records.AuditsRecord;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSON;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * jOOQ-backed {@link AuditRepository}.
 *
 * <p>Column-mapping notes:
 * <ul>
 *   <li>{@code type} — stored as the enum's {@code name()}. Keeps historical
 *       rows readable even if the Java package moves.</li>
 *   <li>{@code occurred_at} — codegen picked {@code OffsetDateTime} for
 *       {@code TIMESTAMPTZ}. We round-trip through UTC
 *       ({@code Instant.atOffset(UTC)} on write, {@code .toInstant()} on read).</li>
 *   <li>{@code details} — jOOQ 3.20 maps PG {@code JSONB} to {@link JSON}
 *       (not JSONB), so we pass through raw text with {@link JSON#valueOf(String)}
 *       on write and {@link JSON#data()} on read. PG JSONB normalises whitespace
 *       and key order — tests assert on content, not byte-identity.</li>
 * </ul>
 */
@Repository
class AuditJooqRepositoryImpl implements AuditRepository {

    private final DSLContext dsl;

    AuditJooqRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void save(Audit audit) {
        Objects.requireNonNull(audit, "audit");
        dsl.insertInto(Tables.AUDITS)
                .set(Tables.AUDITS.ID, audit.id())
                .set(Tables.AUDITS.OCCURRED_AT, audit.occurredAt().atOffset(ZoneOffset.UTC))
                .set(Tables.AUDITS.TYPE, audit.type().name())
                .set(Tables.AUDITS.ACTOR_USER_ID, audit.actorUserId())
                .set(Tables.AUDITS.TARGET_USER_ID, audit.targetUserId())
                .set(Tables.AUDITS.DETAILS, audit.details() == null ? null : JSON.valueOf(audit.details()))
                .set(Tables.AUDITS.REQUEST_ID, audit.requestId())
                .execute();
    }

    @Override
    public PageResult<Audit> list(PageQuery page, AuditType type, UUID actorUserId, UUID targetUserId) {
        Objects.requireNonNull(page, "page");
        Condition where = buildWhere(type, actorUserId, targetUserId);

        List<AuditsRecord> rows = dsl.selectFrom(Tables.AUDITS)
                .where(where)
                .orderBy(Tables.AUDITS.OCCURRED_AT.desc(), Tables.AUDITS.ID.desc())
                .limit(page.size())
                .offset(page.offset())
                .fetch();

        List<Audit> items = new ArrayList<>(rows.size());
        for (AuditsRecord r : rows) {
            items.add(toDomain(r));
        }
        return PageResult.of(items, count(type, actorUserId, targetUserId), page);
    }

    @Override
    public long count(AuditType type, UUID actorUserId, UUID targetUserId) {
        return dsl.fetchCount(Tables.AUDITS, buildWhere(type, actorUserId, targetUserId));
    }

    private static Condition buildWhere(AuditType type, UUID actorUserId, UUID targetUserId) {
        Condition where = DSL.noCondition();
        if (type != null) {
            where = where.and(Tables.AUDITS.TYPE.eq(type.name()));
        }
        if (actorUserId != null) {
            where = where.and(Tables.AUDITS.ACTOR_USER_ID.eq(actorUserId));
        }
        if (targetUserId != null) {
            where = where.and(Tables.AUDITS.TARGET_USER_ID.eq(targetUserId));
        }
        return where;
    }

    private static Audit toDomain(AuditsRecord r) {
        OffsetDateTime occurred = r.getOccurredAt();
        JSON details = r.getDetails();
        return new Audit(
                r.getId(),
                occurred.toInstant(),
                AuditType.valueOf(r.getType()),
                r.getActorUserId(),
                r.getTargetUserId(),
                details == null ? null : details.data(),
                r.getRequestId()
        );
    }
}
