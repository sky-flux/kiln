package com.skyflux.kiln.dict.internal;

import com.skyflux.kiln.dict.api.DictQueryService;
import com.skyflux.kiln.dict.domain.DictItem;
import com.skyflux.kiln.dict.repo.DictItemJooqRepository;
import com.skyflux.kiln.tenant.api.TenantContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
class DictQueryServiceImpl implements DictQueryService {

    private static final Duration TTL = Duration.ofHours(1);

    private final DictItemJooqRepository itemRepo;
    private final RedisTemplate<String, Object> redisTemplate;

    DictQueryServiceImpl(DictItemJooqRepository itemRepo,
                         RedisTemplate<String, Object> redisTemplate) {
        this.itemRepo = itemRepo;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<DictItem> getItems(String typeCode) {
        String key = cacheKey(typeCode);
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached instanceof List<?> list) {
            return (List<DictItem>) list;
        }
        List<DictItem> items = itemRepo.findActiveByTypeCode(typeCode);
        redisTemplate.opsForValue().set(key, items, TTL);
        return items;
    }

    void evict(String typeCode) {
        redisTemplate.delete(cacheKey(typeCode));
    }

    private static String cacheKey(String typeCode) {
        UUID tenantId = TenantContext.CURRENT.isBound() ? TenantContext.CURRENT.get() : null;
        return "dict:" + tenantId + ":" + typeCode;
    }
}
