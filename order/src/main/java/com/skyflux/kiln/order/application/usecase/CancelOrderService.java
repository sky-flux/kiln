package com.skyflux.kiln.order.application.usecase;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.order.application.port.in.CancelOrderUseCase;
import com.skyflux.kiln.order.application.port.out.OrderRepository;
import com.skyflux.kiln.order.api.OrderEvent;
import com.skyflux.kiln.order.domain.model.Order;
import com.skyflux.kiln.order.domain.model.OrderId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CancelOrderService implements CancelOrderUseCase {

    private final OrderRepository repo;
    private final ApplicationEventPublisher publisher;

    public CancelOrderService(OrderRepository repo, ApplicationEventPublisher publisher) {
        this.repo = repo;
        this.publisher = publisher;
    }

    @Override
    @Transactional
    public Order execute(OrderId id) {
        Order order = repo.findById(id)
                .orElseThrow(() -> new AppException(AppCode.NOT_FOUND));
        Order cancelled = order.cancel();
        repo.save(cancelled);
        publisher.publishEvent(new OrderEvent.OrderCancelled(
                cancelled.id(), cancelled.tenantId(), Instant.now()));
        return cancelled;
    }
}
