package com.skyflux.kiln.product.application.usecase;

import com.skyflux.kiln.product.application.port.in.CreateProductUseCase;
import com.skyflux.kiln.product.application.port.out.ProductRepository;
import com.skyflux.kiln.product.domain.model.Product;
import com.skyflux.kiln.tenant.api.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
class CreateProductService implements CreateProductUseCase {

    private final ProductRepository repo;

    CreateProductService(ProductRepository repo) {
        this.repo = repo;
    }

    @Override
    public Product execute(Command cmd) {
        Objects.requireNonNull(cmd, "cmd");
        UUID tenantId = TenantContext.CURRENT.get();
        Product p = Product.create(tenantId, cmd.code(), cmd.name(), cmd.description(), cmd.price());
        repo.save(p);
        return p;
    }
}
