package com.skyflux.kiln.product.adapter.in.web;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.skyflux.kiln.common.money.Money;
import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.common.result.R;
import com.skyflux.kiln.product.application.port.in.CreateProductUseCase;
import com.skyflux.kiln.product.application.port.in.DeleteProductUseCase;
import com.skyflux.kiln.product.application.port.in.GetProductUseCase;
import com.skyflux.kiln.product.application.port.in.ListProductsUseCase;
import com.skyflux.kiln.product.application.port.in.UpdateProductUseCase;
import com.skyflux.kiln.product.domain.model.Product;
import com.skyflux.kiln.product.domain.model.ProductId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/products")
class ProductController {

    private final CreateProductUseCase createUseCase;
    private final GetProductUseCase getUseCase;
    private final UpdateProductUseCase updateUseCase;
    private final DeleteProductUseCase deleteUseCase;
    private final ListProductsUseCase listUseCase;

    ProductController(CreateProductUseCase createUseCase, GetProductUseCase getUseCase,
                      UpdateProductUseCase updateUseCase, DeleteProductUseCase deleteUseCase,
                      ListProductsUseCase listUseCase) {
        this.createUseCase = createUseCase;
        this.getUseCase = getUseCase;
        this.updateUseCase = updateUseCase;
        this.deleteUseCase = deleteUseCase;
        this.listUseCase = listUseCase;
    }

    @PostMapping
    @SaCheckLogin
    @ResponseStatus(HttpStatus.CREATED)
    R<ProductResponse> create(@Valid @RequestBody CreateProductRequest req) {
        Money price = Money.of(req.priceAmount().toPlainString(), req.priceCurrency());
        return R.ok(ProductResponse.from(
                createUseCase.execute(new CreateProductUseCase.Command(
                        req.code(), req.name(), req.description(), price))));
    }

    @GetMapping
    @SaCheckLogin
    R<PageResult<ProductResponse>> list(@Valid PageQuery query) {
        return R.ok(listUseCase.execute(query).map(ProductResponse::from));
    }

    @GetMapping("/{id}")
    @SaCheckLogin
    R<ProductResponse> get(@PathVariable String id) {
        return R.ok(ProductResponse.from(getUseCase.execute(ProductId.of(id))));
    }

    @PutMapping("/{id}")
    @SaCheckLogin
    R<ProductResponse> update(@PathVariable String id, @Valid @RequestBody UpdateProductRequest req) {
        Money price = Money.of(req.priceAmount().toPlainString(), req.priceCurrency());
        return R.ok(ProductResponse.from(
                updateUseCase.execute(new UpdateProductUseCase.Command(
                        ProductId.of(id), req.name(), req.description(), price))));
    }

    @DeleteMapping("/{id}")
    @SaCheckRole("ADMIN")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) {
        deleteUseCase.execute(ProductId.of(id));
    }

    record CreateProductRequest(
            @NotBlank @Size(max = 100) String code,
            @NotBlank @Size(max = 200) String name,
            String description,
            @NotNull @DecimalMin("0") BigDecimal priceAmount,
            @NotBlank @Size(max = 3) String priceCurrency) {}

    record UpdateProductRequest(
            @NotBlank @Size(max = 200) String name,
            String description,
            @NotNull @DecimalMin("0") BigDecimal priceAmount,
            @NotBlank @Size(max = 3) String priceCurrency) {}

    record ProductResponse(String id, String code, String name, String description,
                           BigDecimal priceAmount, String priceCurrency, String status) {
        static ProductResponse from(Product p) {
            return new ProductResponse(
                    p.id().value().toString(), p.code(), p.name(), p.description(),
                    p.price().amount(), p.price().currency().getCurrencyCode(), p.status());
        }
    }
}
