package com.skyflux.kiln.order.adapter.in.web;

import java.math.BigDecimal;

record OrderItemResponse(
        String productId,
        String productCode,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal subtotal) {}
