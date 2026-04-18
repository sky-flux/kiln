package com.skyflux.kiln.product.application.port.out;

import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.product.domain.model.Product;
import com.skyflux.kiln.product.domain.model.ProductId;

import java.util.Optional;

public interface ProductRepository {
    Optional<Product> findById(ProductId id);
    void save(Product product);
    PageResult<Product> listActive(PageQuery query);
}
