package com.skyflux.kiln.dict.internal;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.dict.domain.DictItem;
import com.skyflux.kiln.dict.domain.DictType;
import com.skyflux.kiln.dict.repo.DictItemJooqRepository;
import com.skyflux.kiln.dict.repo.DictTypeJooqRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
class DictService {

    private final DictTypeJooqRepository typeRepo;
    private final DictItemJooqRepository itemRepo;
    private final DictQueryServiceImpl cache;

    DictService(DictTypeJooqRepository typeRepo, DictItemJooqRepository itemRepo,
                DictQueryServiceImpl cache) {
        this.typeRepo = typeRepo;
        this.itemRepo = itemRepo;
        this.cache = cache;
    }

    @Transactional
    public DictType createType(String code, String name, UUID tenantId) {
        if (typeRepo.findByCode(code).isPresent()) {
            throw new AppException(AppCode.CONFLICT, "Dict type code already exists: " + code);
        }
        DictType type = new DictType(Ids.next(), code, name, false, tenantId, null);
        typeRepo.save(type);
        return type;
    }

    @Transactional(readOnly = true)
    public List<DictType> listTypes() {
        return typeRepo.findAll();
    }

    @Transactional
    public DictItem addItem(String typeCode, String code, String label, int sortOrder) {
        DictType type = typeRepo.findByCode(typeCode)
            .orElseThrow(() -> new AppException(AppCode.NOT_FOUND, "Dict type not found: " + typeCode));
        DictItem item = new DictItem(Ids.next(), type.id(), code, label, sortOrder, true,
            type.tenantId(), null);
        itemRepo.save(item);
        cache.evict(typeCode);
        return item;
    }

    @Transactional
    public DictItem updateItem(UUID itemId, String label, int sortOrder, boolean isActive,
                               String typeCode) {
        DictItem existing = itemRepo.findById(itemId)
            .orElseThrow(() -> new AppException(AppCode.NOT_FOUND));
        DictItem updated = new DictItem(existing.id(), existing.typeId(), existing.code(),
            label, sortOrder, isActive, existing.tenantId(), existing.createdAt());
        itemRepo.save(updated);
        cache.evict(typeCode);
        return updated;
    }

    @Transactional
    public void deleteItem(UUID itemId, String typeCode) {
        itemRepo.findById(itemId).orElseThrow(() -> new AppException(AppCode.NOT_FOUND));
        itemRepo.delete(itemId);
        cache.evict(typeCode);
    }
}
