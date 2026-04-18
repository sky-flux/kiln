package com.skyflux.kiln.product.application.port.in;

import com.skyflux.kiln.product.domain.model.Product;
import com.skyflux.kiln.product.domain.model.ProductId;

public interface GetProductUseCase {
    Product execute(ProductId id);
}
