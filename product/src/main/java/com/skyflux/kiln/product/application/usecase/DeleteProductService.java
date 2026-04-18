package com.skyflux.kiln.product.application.usecase;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.product.application.port.in.DeleteProductUseCase;
import com.skyflux.kiln.product.application.port.out.ProductRepository;
import com.skyflux.kiln.product.domain.model.Product;
import com.skyflux.kiln.product.domain.model.ProductId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class DeleteProductService implements DeleteProductUseCase {

    private final ProductRepository repo;

    DeleteProductService(ProductRepository repo) {
        this.repo = repo;
    }

    @Override
    public void execute(ProductId id) {
        Product p = repo.findById(id)
                .orElseThrow(() -> new AppException(AppCode.NOT_FOUND));
        repo.save(p.deactivate());
    }
}
