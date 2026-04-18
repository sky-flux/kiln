package com.skyflux.kiln.order.domain.exception;

import com.skyflux.kiln.order.domain.model.OrderStatus;

public class InvalidOrderTransitionException extends RuntimeException {
    public InvalidOrderTransitionException(OrderStatus from, OrderStatus to) {
        super("Cannot transition order from " + from + " to " + to);
    }
}
