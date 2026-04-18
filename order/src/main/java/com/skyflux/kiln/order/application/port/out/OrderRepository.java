package com.skyflux.kiln.order.application.port.out;

import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.order.domain.model.Order;
import com.skyflux.kiln.order.domain.model.OrderId;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    Optional<Order> findById(OrderId id);
    void save(Order order);
    PageResult<Order> listByUser(UUID userId, PageQuery query);
    PageResult<Order> listAll(PageQuery query);
}
