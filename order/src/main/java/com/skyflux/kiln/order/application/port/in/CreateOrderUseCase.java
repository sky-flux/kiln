package com.skyflux.kiln.order.application.port.in;

import com.skyflux.kiln.order.domain.model.Order;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CreateOrderUseCase {

    record ItemRequest(
            UUID productId,
            String productCode,
            String productName,
            BigDecimal unitPrice,
            String currency,
            int quantity) {}

    record Command(UUID tenantId, UUID userId, List<ItemRequest> items, String note) {}

    Order execute(Command cmd);
}
