package com.skyflux.kiln.dict.repo;

import com.skyflux.kiln.dict.domain.DictItem;
import com.skyflux.kiln.infra.jooq.generated.Tables;
import com.skyflux.kiln.infra.jooq.generated.tables.records.DictItemsRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DictItemJooqRepository {

    private final DSLContext dsl;

    DictItemJooqRepository(DSLContext dsl) { this.dsl = dsl; }

    /** Returns active items for a type code, ordered by sort_order. RLS auto-filters. */
    public List<DictItem> findActiveByTypeCode(String typeCode) {
        Objects.requireNonNull(typeCode, "typeCode");
        return dsl.select(Tables.DICT_ITEMS.fields())
            .from(Tables.DICT_ITEMS)
            .join(Tables.DICT_TYPES).on(Tables.DICT_TYPES.ID.eq(Tables.DICT_ITEMS.TYPE_ID))
            .where(Tables.DICT_TYPES.CODE.eq(typeCode))
            .and(Tables.DICT_ITEMS.IS_ACTIVE.isTrue())
            .orderBy(Tables.DICT_ITEMS.SORT_ORDER, Tables.DICT_ITEMS.CODE)
            .fetch()
            .map(r -> toItem(r.into(Tables.DICT_ITEMS)));
    }

    public Optional<DictItem> findById(UUID id) {
        Objects.requireNonNull(id, "id");
        return dsl.selectFrom(Tables.DICT_ITEMS)
            .where(Tables.DICT_ITEMS.ID.eq(id))
            .fetchOptional()
            .map(this::toItem);
    }

    public void save(DictItem item) {
        Objects.requireNonNull(item, "item");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.insertInto(Tables.DICT_ITEMS)
            .set(Tables.DICT_ITEMS.ID, item.id())
            .set(Tables.DICT_ITEMS.TYPE_ID, item.typeId())
            .set(Tables.DICT_ITEMS.CODE, item.code())
            .set(Tables.DICT_ITEMS.LABEL, item.label())
            .set(Tables.DICT_ITEMS.SORT_ORDER, item.sortOrder())
            .set(Tables.DICT_ITEMS.IS_ACTIVE, item.isActive())
            .set(Tables.DICT_ITEMS.TENANT_ID, item.tenantId())
            .set(Tables.DICT_ITEMS.CREATED_AT, now)
            .onConflict(Tables.DICT_ITEMS.ID)
            .doUpdate()
            .set(Tables.DICT_ITEMS.LABEL, item.label())
            .set(Tables.DICT_ITEMS.SORT_ORDER, item.sortOrder())
            .set(Tables.DICT_ITEMS.IS_ACTIVE, item.isActive())
            .execute();
    }

    public void delete(UUID id) {
        dsl.deleteFrom(Tables.DICT_ITEMS).where(Tables.DICT_ITEMS.ID.eq(id)).execute();
    }

    private DictItem toItem(DictItemsRecord r) {
        return new DictItem(r.getId(), r.getTypeId(), r.getCode(), r.getLabel(),
            r.getSortOrder() == null ? 0 : r.getSortOrder(),
            Boolean.TRUE.equals(r.getIsActive()), r.getTenantId(),
            r.getCreatedAt().toInstant());
    }
}
