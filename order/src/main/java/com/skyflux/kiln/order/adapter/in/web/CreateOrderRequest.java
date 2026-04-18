package com.skyflux.kiln.order.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

record CreateOrderRequest(
        @NotEmpty List<@Valid OrderItemRequest> items,
        String note) {}
