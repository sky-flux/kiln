package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.domain.AuditEvent;
import com.skyflux.kiln.audit.domain.AuditEventType;
import com.skyflux.kiln.audit.repo.AuditEventJooqRepository;
import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.infra.jooq.generated.Tables;
import com.skyflux.kiln.infra.jooq.generated.tables.records.AuditEventsRecord;
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
 * jOOQ-backed {@link AuditEventJooqRepository}.
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
class AuditEventJooqRepositoryImpl implements AuditEventJooqRepository {

    private final DSLContext dsl;

    AuditEventJooqRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void save(AuditEvent event) {
        Objects.requireNonNull(event, "event");
        dsl.insertInto(Tables.AUDIT_EVENTS)
                .set(Tables.AUDIT_EVENTS.ID, event.id())
                .set(Tables.AUDIT_EVENTS.OCCURRED_AT, event.occurredAt().atOffset(ZoneOffset.UTC))
                .set(Tables.AUDIT_EVENTS.TYPE, event.type().name())
                .set(Tables.AUDIT_EVENTS.ACTOR_USER_ID, event.actorUserId())
                .set(Tables.AUDIT_EVENTS.TARGET_USER_ID, event.targetUserId())
                .set(Tables.AUDIT_EVENTS.DETAILS, event.details() == null ? null : JSON.valueOf(event.details()))
                .set(Tables.AUDIT_EVENTS.REQUEST_ID, event.requestId())
                .execute();
    }

    @Override
    public PageResult<AuditEvent> list(PageQuery page, AuditEventType type, UUID actorUserId, UUID targetUserId) {
        Objects.requireNonNull(page, "page");
        Condition where = buildWhere(type, actorUserId, targetUserId);

        List<AuditEventsRecord> rows = dsl.selectFrom(Tables.AUDIT_EVENTS)
                .where(where)
                .orderBy(Tables.AUDIT_EVENTS.OCCURRED_AT.desc(), Tables.AUDIT_EVENTS.ID.desc())
                .limit(page.size())
                .offset(page.offset())
                .fetch();

        List<AuditEvent> items = new ArrayList<>(rows.size());
        for (AuditEventsRecord r : rows) {
            items.add(toDomain(r));
        }
        return PageResult.of(items, count(type, actorUserId, targetUserId), page);
    }

    @Override
    public long count(AuditEventType type, UUID actorUserId, UUID targetUserId) {
        return dsl.fetchCount(Tables.AUDIT_EVENTS, buildWhere(type, actorUserId, targetUserId));
    }

    private static Condition buildWhere(AuditEventType type, UUID actorUserId, UUID targetUserId) {
        Condition where = DSL.noCondition();
        if (type != null) {
            where = where.and(Tables.AUDIT_EVENTS.TYPE.eq(type.name()));
        }
        if (actorUserId != null) {
            where = where.and(Tables.AUDIT_EVENTS.ACTOR_USER_ID.eq(actorUserId));
        }
        if (targetUserId != null) {
            where = where.and(Tables.AUDIT_EVENTS.TARGET_USER_ID.eq(targetUserId));
        }
        return where;
    }

    private static AuditEvent toDomain(AuditEventsRecord r) {
        OffsetDateTime occurred = r.getOccurredAt();
        JSON details = r.getDetails();
        return new AuditEvent(
                r.getId(),
                occurred.toInstant(),
                AuditEventType.valueOf(r.getType()),
                r.getActorUserId(),
                r.getTargetUserId(),
                details == null ? null : details.data(),
                r.getRequestId()
        );
    }
}
