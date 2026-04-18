package com.skyflux.kiln.dict.internal;

import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.dict.domain.DictItem;
import com.skyflux.kiln.dict.repo.DictItemJooqRepository;
import com.skyflux.kiln.tenant.api.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DictQueryServiceImplTest {

    @Mock DictItemJooqRepository itemRepo;
    @Mock RedisTemplate<String, Object> redisTemplate;
    @Mock ValueOperations<String, Object> valueOps;
    @InjectMocks DictQueryServiceImpl service;

    private static final UUID TENANT = Ids.next();
    private static final DictItem ITEM = new DictItem(
        Ids.next(), Ids.next(), "MALE", "男", 1, true, null, Instant.now());

    @Test void shouldReturnCachedItemsWhenCacheHits() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("dict:" + TENANT + ":GENDER")).thenReturn(List.of(ITEM));

        List<DictItem> result = ScopedValue.where(TenantContext.CURRENT, TENANT)
            .call(() -> service.getItems("GENDER"));

        assertThat(result).containsExactly(ITEM);
        verifyNoInteractions(itemRepo);
    }

    @Test void shouldQueryDbAndCacheOnMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("dict:" + TENANT + ":GENDER")).thenReturn(null);
        when(itemRepo.findActiveByTypeCode("GENDER")).thenReturn(List.of(ITEM));

        List<DictItem> result = ScopedValue.where(TenantContext.CURRENT, TENANT)
            .call(() -> service.getItems("GENDER"));

        assertThat(result).containsExactly(ITEM);
        verify(valueOps).set(eq("dict:" + TENANT + ":GENDER"), eq(List.of(ITEM)), any());
    }

    @Test void shouldUseNullKeyForUnauthenticatedContext() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("dict:null:GENDER")).thenReturn(List.of(ITEM));

        // No TenantContext bound — key uses "null" as tenantId segment
        List<DictItem> result = service.getItems("GENDER");
        assertThat(result).containsExactly(ITEM);
    }
}
