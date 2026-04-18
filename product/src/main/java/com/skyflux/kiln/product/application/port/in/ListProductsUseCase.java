package com.skyflux.kiln.product.application.port.in;

import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.product.domain.model.Product;

public interface ListProductsUseCase {
    PageResult<Product> execute(PageQuery query);
}
