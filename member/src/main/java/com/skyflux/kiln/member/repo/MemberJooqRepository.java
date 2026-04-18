package com.skyflux.kiln.member.repo;

import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.infra.jooq.generated.Tables;
import com.skyflux.kiln.infra.jooq.generated.tables.records.MembersRecord;
import com.skyflux.kiln.member.domain.Member;
import com.skyflux.kiln.member.domain.MemberId;
import com.skyflux.kiln.member.domain.MemberLevel;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MemberJooqRepository {

    private final DSLContext dsl;

    public MemberJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** UPSERT on conflict(ID): update level/points/status/updated_at. */
    public void save(Member member) {
        Objects.requireNonNull(member, "member");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.insertInto(Tables.MEMBERS)
                .set(Tables.MEMBERS.ID, member.id().value())
                .set(Tables.MEMBERS.TENANT_ID, member.tenantId())
                .set(Tables.MEMBERS.USER_ID, member.userId())
                .set(Tables.MEMBERS.LEVEL, member.level().name())
                .set(Tables.MEMBERS.POINTS, member.points())
                .set(Tables.MEMBERS.STATUS, member.status())
                .set(Tables.MEMBERS.UPDATED_AT, now)
                .onConflict(Tables.MEMBERS.USER_ID, Tables.MEMBERS.TENANT_ID)
                .doUpdate()
                .set(Tables.MEMBERS.LEVEL, member.level().name())
                .set(Tables.MEMBERS.POINTS, member.points())
                .set(Tables.MEMBERS.STATUS, member.status())
                .set(Tables.MEMBERS.UPDATED_AT, now)
                .execute();
    }

    public Optional<Member> findByUserId(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        return dsl.selectFrom(Tables.MEMBERS)
                .where(Tables.MEMBERS.USER_ID.eq(userId))
                .fetchOptional()
                .map(this::toAggregate);
    }

    public PageResult<Member> listByTenant(UUID tenantId, PageQuery query) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(query, "query");
        var cond = Tables.MEMBERS.TENANT_ID.eq(tenantId);
        long total = Optional.ofNullable(
                dsl.selectCount().from(Tables.MEMBERS).where(cond).fetchOne(0, Long.class)
        ).orElse(0L);
        List<Member> items = dsl.selectFrom(Tables.MEMBERS)
                .where(cond)
                .orderBy(Tables.MEMBERS.JOINED_AT.desc())
                .limit(query.size()).offset(query.offset())
                .fetch()
                .map(this::toAggregate);
        return PageResult.of(items, total, query);
    }

    private Member toAggregate(MembersRecord r) {
        return Member.reconstitute(
                new MemberId(r.getId()),
                r.getTenantId(),
                r.getUserId(),
                MemberLevel.valueOf(r.getLevel()),
                r.getPoints(),
                r.getStatus()
        );
    }
}
