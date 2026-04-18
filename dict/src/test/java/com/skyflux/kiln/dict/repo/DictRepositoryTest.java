package com.skyflux.kiln.dict.repo;

import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.dict.domain.DictItem;
import com.skyflux.kiln.dict.domain.DictType;
import com.skyflux.kiln.tenant.api.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = com.skyflux.kiln.dict.config.DictTestApp.class)
class DictRepositoryTest {

    @Autowired DictTypeJooqRepository typeRepo;
    @Autowired DictItemJooqRepository itemRepo;

    @Test void systemTypesAreSeededByMigration() {
        // system types have tenant_id IS NULL and are visible without RLS context
        List<DictType> types = typeRepo.findAll();
        assertThat(types).extracting(DictType::code)
            .contains("GENDER", "YES_NO", "ACTIVE_STATUS");
    }

    @Test void systemItemsVisibleToAnyTenant() {
        UUID tenantId = UUID.fromString("00000000-0000-7000-8000-000000000001");
        List<DictItem> items = ScopedValue.where(TenantContext.CURRENT, tenantId)
            .call(() -> itemRepo.findActiveByTypeCode("GENDER"));
        assertThat(items).extracting(DictItem::code).contains("MALE", "FEMALE", "OTHER");
    }

    @Test void tenantTypeCanBeSavedAndRetrievedByCode() {
        UUID tenantA = UUID.fromString("00000000-0000-7000-8000-000000000001");
        String uniqueCode = "CUSTOM_" + Ids.next().toString().substring(0, 8).toUpperCase();

        DictType type = new DictType(Ids.next(), uniqueCode, "自定义", false, tenantA, null);
        typeRepo.save(type);

        assertThat(typeRepo.findByCode(uniqueCode))
            .isPresent()
            .get().extracting(DictType::tenantId).isEqualTo(tenantA);

        // Add an item for this type under tenantA's context
        DictItem item = new DictItem(Ids.next(), type.id(), "VAL1", "值1", 1, true, tenantA, null);
        ScopedValue.where(TenantContext.CURRENT, tenantA).run(() -> itemRepo.save(item));

        // TenantA sees it (system RLS policy allows items where tenant_id matches)
        List<DictItem> forA = ScopedValue.where(TenantContext.CURRENT, tenantA)
            .call(() -> itemRepo.findActiveByTypeCode(uniqueCode));
        assertThat(forA).hasSize(1).extracting(DictItem::code).containsExactly("VAL1");
    }
}
