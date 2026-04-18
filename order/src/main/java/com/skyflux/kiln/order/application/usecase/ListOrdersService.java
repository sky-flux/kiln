package com.skyflux.kiln.order.application.usecase;

import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.order.application.port.in.ListOrdersUseCase;
import com.skyflux.kiln.order.application.port.out.OrderRepository;
import com.skyflux.kiln.order.domain.model.Order;
import org.springframework.stereotype.Service;

@Service
public class ListOrdersService implements ListOrdersUseCase {

    private final OrderRepository repo;

    public ListOrdersService(OrderRepository repo) {
        this.repo = repo;
    }

    @Override
    public PageResult<Order> execute(Command cmd) {
        return repo.listByUser(cmd.userId(), cmd.query());
    }
}
