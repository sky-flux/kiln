package com.skyflux.kiln.product.application.usecase;

import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.product.application.port.in.ListProductsUseCase;
import com.skyflux.kiln.product.application.port.out.ProductRepository;
import com.skyflux.kiln.product.domain.model.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class ListProductsService implements ListProductsUseCase {

    private final ProductRepository repo;

    ListProductsService(ProductRepository repo) {
        this.repo = repo;
    }

    @Override
    public PageResult<Product> execute(PageQuery query) {
        return repo.listActive(query);
    }
}
