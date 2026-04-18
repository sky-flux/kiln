package com.skyflux.kiln.order.application.usecase;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.order.application.port.in.ConfirmOrderUseCase;
import com.skyflux.kiln.order.application.port.out.OrderRepository;
import com.skyflux.kiln.order.domain.model.Order;
import com.skyflux.kiln.order.domain.model.OrderId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfirmOrderService implements ConfirmOrderUseCase {

    private final OrderRepository repo;

    public ConfirmOrderService(OrderRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public Order execute(OrderId id) {
        Order order = repo.findById(id)
                .orElseThrow(() -> new AppException(AppCode.NOT_FOUND));
        Order confirmed = order.confirm();
        repo.save(confirmed);
        return confirmed;
    }
}
