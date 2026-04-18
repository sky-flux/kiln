package com.skyflux.kiln.order.application.port.in;

import com.skyflux.kiln.order.domain.model.Order;
import com.skyflux.kiln.order.domain.model.OrderId;

public interface PayOrderUseCase {
    Order execute(OrderId id);
}
