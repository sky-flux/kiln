package com.skyflux.kiln.order.application.port.in;

import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.order.domain.model.Order;

import java.util.UUID;

public interface ListOrdersUseCase {
    record Command(UUID userId, PageQuery query) {}

    PageResult<Order> execute(Command cmd);
}
