package com.skyflux.kiln.order.application.usecase;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.order.application.port.in.GetOrderUseCase;
import com.skyflux.kiln.order.application.port.out.OrderRepository;
import com.skyflux.kiln.order.domain.model.Order;
import com.skyflux.kiln.order.domain.model.OrderId;
import org.springframework.stereotype.Service;

@Service
public class GetOrderService implements GetOrderUseCase {

    private final OrderRepository repo;

    public GetOrderService(OrderRepository repo) {
        this.repo = repo;
    }

    @Override
    public Order execute(OrderId id) {
        return repo.findById(id)
                .orElseThrow(() -> new AppException(AppCode.NOT_FOUND));
    }
}
