package com.skyflux.kiln.dict.internal;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.skyflux.kiln.common.result.R;
import com.skyflux.kiln.dict.api.DictQueryService;
import com.skyflux.kiln.dict.domain.DictItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
class DictItemController {

    private final DictQueryService queryService;
    private final DictService service;

    DictItemController(DictQueryService queryService, DictService service) {
        this.queryService = queryService;
        this.service = service;
    }

    @GetMapping("/api/v1/dict/{typeCode}/items")
    @SaCheckLogin
    R<List<DictItemResponse>> items(@PathVariable String typeCode) {
        return R.ok(queryService.getItems(typeCode).stream().map(DictItemResponse::from).toList());
    }

    @PostMapping("/api/v1/admin/dict/types/{typeCode}/items")
    @SaCheckRole("ADMIN")
    @ResponseStatus(HttpStatus.CREATED)
    R<DictItemResponse> addItem(@PathVariable String typeCode,
                                @Valid @RequestBody AddDictItemRequest req) {
        return R.ok(DictItemResponse.from(
            service.addItem(typeCode, req.code(), req.label(), req.sortOrder())));
    }

    @PutMapping("/api/v1/admin/dict/items/{itemId}")
    @SaCheckRole("ADMIN")
    R<DictItemResponse> updateItem(@PathVariable UUID itemId,
                                   @Valid @RequestBody UpdateDictItemRequest req) {
        return R.ok(DictItemResponse.from(
            service.updateItem(itemId, req.label(), req.sortOrder(), req.isActive(), req.typeCode())));
    }

    @DeleteMapping("/api/v1/admin/dict/items/{itemId}")
    @SaCheckRole("ADMIN")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteItem(@PathVariable UUID itemId, @RequestParam String typeCode) {
        service.deleteItem(itemId, typeCode);
    }

    record AddDictItemRequest(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 200) String label,
        int sortOrder) {}

    record UpdateDictItemRequest(
        @NotBlank @Size(max = 200) String label,
        int sortOrder,
        boolean isActive,
        @NotBlank String typeCode) {}

    record DictItemResponse(String id, String code, String label, int sortOrder, boolean isActive) {
        static DictItemResponse from(DictItem i) {
            return new DictItemResponse(i.id().toString(), i.code(), i.label(),
                i.sortOrder(), i.isActive());
        }
    }
}
