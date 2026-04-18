package com.skyflux.kiln.product.application.port.in;

import com.skyflux.kiln.product.domain.model.ProductId;

public interface DeleteProductUseCase {
    void execute(ProductId id);
}
