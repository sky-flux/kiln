package com.skyflux.kiln.dict.repo;

import com.skyflux.kiln.dict.domain.DictType;
import com.skyflux.kiln.infra.jooq.generated.Tables;
import com.skyflux.kiln.infra.jooq.generated.tables.records.DictTypesRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DictTypeJooqRepository {

    private final DSLContext dsl;

    DictTypeJooqRepository(DSLContext dsl) { this.dsl = dsl; }

    public List<DictType> findAll() {
        return dsl.selectFrom(Tables.DICT_TYPES)
            .orderBy(Tables.DICT_TYPES.CODE)
            .fetch()
            .map(this::toType);
    }

    public Optional<DictType> findByCode(String code) {
        Objects.requireNonNull(code, "code");
        return dsl.selectFrom(Tables.DICT_TYPES)
            .where(Tables.DICT_TYPES.CODE.eq(code))
            .fetchOptional()
            .map(this::toType);
    }

    public void save(DictType type) {
        Objects.requireNonNull(type, "type");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.insertInto(Tables.DICT_TYPES)
            .set(Tables.DICT_TYPES.ID, type.id())
            .set(Tables.DICT_TYPES.CODE, type.code())
            .set(Tables.DICT_TYPES.NAME, type.name())
            .set(Tables.DICT_TYPES.IS_SYSTEM, type.isSystem())
            .set(Tables.DICT_TYPES.TENANT_ID, type.tenantId())
            .set(Tables.DICT_TYPES.CREATED_AT, now)
            .onConflict(Tables.DICT_TYPES.ID)
            .doUpdate()
            .set(Tables.DICT_TYPES.NAME, type.name())
            .execute();
    }

    public void delete(UUID id) {
        dsl.deleteFrom(Tables.DICT_TYPES).where(Tables.DICT_TYPES.ID.eq(id)).execute();
    }

    private DictType toType(DictTypesRecord r) {
        return new DictType(r.getId(), r.getCode(), r.getName(),
            Boolean.TRUE.equals(r.getIsSystem()), r.getTenantId(),
            r.getCreatedAt().toInstant());
    }
}
