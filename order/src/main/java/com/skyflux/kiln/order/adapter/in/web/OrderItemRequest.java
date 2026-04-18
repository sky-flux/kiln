package com.skyflux.kiln.order.adapter.in.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

record OrderItemRequest(
        @NotNull UUID productId,
        @NotBlank String productCode,
        @NotBlank String productName,
        @NotNull @DecimalMin("0") BigDecimal unitPrice,
        @NotBlank @Size(max = 3) String currency,
        @Min(1) int quantity) {}
