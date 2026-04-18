package com.skyflux.kiln.product.application.port.in;

import com.skyflux.kiln.common.money.Money;
import com.skyflux.kiln.product.domain.model.Product;
import com.skyflux.kiln.product.domain.model.ProductId;

public interface UpdateProductUseCase {
    record Command(ProductId id, String name, String description, Money price) {}
    Product execute(Command cmd);
}
