package com.skyflux.kiln.product.application.usecase;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.product.application.port.in.UpdateProductUseCase;
import com.skyflux.kiln.product.application.port.out.ProductRepository;
import com.skyflux.kiln.product.domain.model.Product;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class UpdateProductService implements UpdateProductUseCase {

    private final ProductRepository repo;

    UpdateProductService(ProductRepository repo) {
        this.repo = repo;
    }

    @Override
    public Product execute(Command cmd) {
        Product p = repo.findById(cmd.id())
                .orElseThrow(() -> new AppException(AppCode.NOT_FOUND));
        Product updated = p.updateDetails(cmd.name(), cmd.description(), cmd.price());
        repo.save(updated);
        return updated;
    }
}
