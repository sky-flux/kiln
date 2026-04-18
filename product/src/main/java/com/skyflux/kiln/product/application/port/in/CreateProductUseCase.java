package com.skyflux.kiln.product.application.port.in;

import com.skyflux.kiln.common.money.Money;
import com.skyflux.kiln.product.domain.model.Product;

public interface CreateProductUseCase {
    record Command(String code, String name, String description, Money price) {}
    Product execute(Command cmd);
}
