package com.skyflux.kiln.product.application.usecase;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.product.application.port.in.GetProductUseCase;
import com.skyflux.kiln.product.application.port.out.ProductRepository;
import com.skyflux.kiln.product.domain.model.Product;
import com.skyflux.kiln.product.domain.model.ProductId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@Transactional(readOnly = true)
class GetProductService implements GetProductUseCase {

    private final ProductRepository repo;

    GetProductService(ProductRepository repo) {
        this.repo = repo;
    }

    @Override
    public Product execute(ProductId id) {
        Objects.requireNonNull(id, "id");
        return repo.findById(id).orElseThrow(() -> new AppException(AppCode.NOT_FOUND));
    }
}
