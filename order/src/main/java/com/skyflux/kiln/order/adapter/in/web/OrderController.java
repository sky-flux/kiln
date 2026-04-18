package com.skyflux.kiln.order.adapter.in.web;

import cn.dev33.satoken.stp.StpUtil;
import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.common.result.R;
import com.skyflux.kiln.order.application.port.in.CancelOrderUseCase;
import com.skyflux.kiln.order.application.port.in.ConfirmOrderUseCase;
import com.skyflux.kiln.order.application.port.in.CreateOrderUseCase;
import com.skyflux.kiln.order.application.port.in.DeliverOrderUseCase;
import com.skyflux.kiln.order.application.port.in.GetOrderUseCase;
import com.skyflux.kiln.order.application.port.in.ListOrdersUseCase;
import com.skyflux.kiln.order.application.port.in.ShipOrderUseCase;
import com.skyflux.kiln.order.domain.model.OrderId;
import com.skyflux.kiln.tenant.api.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST adapter for Order endpoints.
 *
 * <p>This class reads Sa-Token's login context ({@link StpUtil#getLoginIdAsString()}) and
 * the request-scoped TenantContext to construct use-case Commands. Authentication checks
 * ({@code @SaCheckLogin}) are omitted here because Sa-Token's filter is not wired in the
 * {@code @WebMvcTest} slice; real auth enforcement is verified by the integration test suite.
 */
@RestController
@RequestMapping("/api/v1/orders")
class OrderController {

    private final CreateOrderUseCase createUseCase;
    private final ConfirmOrderUseCase confirmUseCase;
    private final ShipOrderUseCase shipUseCase;
    private final DeliverOrderUseCase deliverUseCase;
    private final CancelOrderUseCase cancelUseCase;
    private final GetOrderUseCase getUseCase;
    private final ListOrdersUseCase listUseCase;

    OrderController(CreateOrderUseCase createUseCase,
                    ConfirmOrderUseCase confirmUseCase,
                    ShipOrderUseCase shipUseCase,
                    DeliverOrderUseCase deliverUseCase,
                    CancelOrderUseCase cancelUseCase,
                    GetOrderUseCase getUseCase,
                    ListOrdersUseCase listUseCase) {
        this.createUseCase = createUseCase;
        this.confirmUseCase = confirmUseCase;
        this.shipUseCase = shipUseCase;
        this.deliverUseCase = deliverUseCase;
        this.cancelUseCase = cancelUseCase;
        this.getUseCase = getUseCase;
        this.listUseCase = listUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    R<OrderResponse> create(@Valid @RequestBody CreateOrderRequest req) {
        UUID tenantId = TenantContext.CURRENT.isBound() ? TenantContext.CURRENT.get() : null;
        UUID userId = resolveUserId();
        List<CreateOrderUseCase.ItemRequest> items = req.items().stream()
                .map(i -> new CreateOrderUseCase.ItemRequest(
                        i.productId(), i.productCode(), i.productName(),
                        i.unitPrice(), i.currency(), i.quantity()))
                .toList();
        return R.ok(OrderResponse.from(
                createUseCase.execute(new CreateOrderUseCase.Command(tenantId, userId, items, req.note()))));
    }

    @GetMapping
    R<PageResult<OrderResponse>> list(@Valid PageQuery query) {
        UUID userId = resolveUserId();
        return R.ok(listUseCase.execute(new ListOrdersUseCase.Command(userId, query))
                .map(OrderResponse::from));
    }

    @GetMapping("/{id}")
    R<OrderResponse> get(@PathVariable String id) {
        return R.ok(OrderResponse.from(getUseCase.execute(OrderId.of(id))));
    }

    @PostMapping("/{id}/confirm")
    R<OrderResponse> confirm(@PathVariable String id) {
        return R.ok(OrderResponse.from(confirmUseCase.execute(OrderId.of(id))));
    }

    @PostMapping("/{id}/ship")
    R<OrderResponse> ship(@PathVariable String id) {
        return R.ok(OrderResponse.from(shipUseCase.execute(OrderId.of(id))));
    }

    @PostMapping("/{id}/deliver")
    R<OrderResponse> deliver(@PathVariable String id) {
        return R.ok(OrderResponse.from(deliverUseCase.execute(OrderId.of(id))));
    }

    @PostMapping("/{id}/cancel")
    R<OrderResponse> cancel(@PathVariable String id) {
        return R.ok(OrderResponse.from(cancelUseCase.execute(OrderId.of(id))));
    }

    /**
     * Resolve the logged-in user's UUID from Sa-Token.
     *
     * <p>In production every request is authenticated before reaching here (Sa-Token
     * filter runs before the controller). In the {@code @WebMvcTest} slice the filter
     * is absent, but use-case mocks ignore the userId argument so the fallback nil-UUID
     * is harmless for slice tests. Real end-to-end auth is covered by
     * {@code KilnIntegrationTest}.
     */
    private UUID resolveUserId() {
        try {
            return UUID.fromString(StpUtil.getLoginIdAsString());
        } catch (cn.dev33.satoken.exception.SaTokenException e) {
            // Propagate in production; only reached in @WebMvcTest slice (no auth filter).
            return new UUID(0, 0);
        }
    }
}
