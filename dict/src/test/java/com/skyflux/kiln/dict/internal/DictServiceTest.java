package com.skyflux.kiln.dict.internal;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.dict.domain.DictItem;
import com.skyflux.kiln.dict.domain.DictType;
import com.skyflux.kiln.dict.repo.DictItemJooqRepository;
import com.skyflux.kiln.dict.repo.DictTypeJooqRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DictServiceTest {

    @Mock DictTypeJooqRepository typeRepo;
    @Mock DictItemJooqRepository itemRepo;
    @Mock DictQueryServiceImpl cache;
    @InjectMocks DictService service;

    @Test void shouldCreateTenantType() {
        when(typeRepo.findByCode("MY_TYPE")).thenReturn(Optional.empty());
        UUID tenantId = Ids.next();
        DictType result = service.createType("MY_TYPE", "我的类型", tenantId);
        assertThat(result.code()).isEqualTo("MY_TYPE");
        assertThat(result.isSystem()).isFalse();
        assertThat(result.tenantId()).isEqualTo(tenantId);
        verify(typeRepo).save(any());
    }

    @Test void shouldRejectDuplicateTypeCode() {
        when(typeRepo.findByCode("GENDER")).thenReturn(Optional.of(
            new DictType(Ids.next(), "GENDER", "性别", true, null, Instant.now())));
        assertThatThrownBy(() -> service.createType("GENDER", "copy", Ids.next()))
            .isInstanceOf(AppException.class)
            .extracting(e -> ((AppException) e).appCode())
            .isEqualTo(AppCode.CONFLICT);
    }

    @Test void shouldAddItemAndEvictCache() {
        DictType type = new DictType(Ids.next(), "GENDER", "性别", false, Ids.next(), Instant.now());
        when(typeRepo.findByCode("GENDER")).thenReturn(Optional.of(type));
        service.addItem("GENDER", "OTHER2", "其他2", 99);
        verify(itemRepo).save(any());
        verify(cache).evict("GENDER");
    }

    @Test void shouldUpdateItemAndEvictCache() {
        UUID id = Ids.next(); UUID typeId = Ids.next();
        DictItem item = new DictItem(id, typeId, "MALE", "男", 1, true, null, Instant.now());
        when(itemRepo.findById(id)).thenReturn(Optional.of(item));
        service.updateItem(id, "男性", 1, true, "GENDER");
        verify(itemRepo).save(any());
        verify(cache).evict("GENDER");
    }

    @Test void shouldDeleteItemAndEvictCache() {
        UUID id = Ids.next();
        DictItem item = new DictItem(id, Ids.next(), "MALE", "男", 1, true, null, Instant.now());
        when(itemRepo.findById(id)).thenReturn(Optional.of(item));
        service.deleteItem(id, "GENDER");
        verify(itemRepo).delete(id);
        verify(cache).evict("GENDER");
    }
}
