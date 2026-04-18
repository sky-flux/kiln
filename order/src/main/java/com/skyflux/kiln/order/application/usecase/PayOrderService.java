package com.skyflux.kiln.order.application.usecase;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.order.api.OrderEvent;
import com.skyflux.kiln.order.application.port.in.PayOrderUseCase;
import com.skyflux.kiln.order.application.port.out.OrderRepository;
import com.skyflux.kiln.order.domain.model.Order;
import com.skyflux.kiln.order.domain.model.OrderId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@Transactional
class PayOrderService implements PayOrderUseCase {

    private final OrderRepository repo;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PayOrderService(OrderRepository repo, ApplicationEventPublisher events, Clock clock) {
        this.repo = repo;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public Order execute(OrderId id) {
        Order order = repo.findById(id)
            .orElseThrow(() -> new AppException(AppCode.NOT_FOUND));
        Order paid = order.pay();
        repo.save(paid);
        events.publishEvent(new OrderEvent.OrderPaid(
            paid.id(), paid.tenantId(), paid.userId(), paid.totalAmount(), clock.instant()));
        return paid;
    }
}
