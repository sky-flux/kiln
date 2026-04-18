package com.skyflux.kiln.product.application.usecase;

import com.skyflux.kiln.common.exception.AppCode;
import com.skyflux.kiln.common.exception.AppException;
import com.skyflux.kiln.common.money.Money;
import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.product.application.port.in.CreateProductUseCase;
import com.skyflux.kiln.product.application.port.in.DeleteProductUseCase;
import com.skyflux.kiln.product.application.port.in.GetProductUseCase;
import com.skyflux.kiln.product.application.port.in.ListProductsUseCase;
import com.skyflux.kiln.product.application.port.in.UpdateProductUseCase;
import com.skyflux.kiln.product.application.port.out.ProductRepository;
import com.skyflux.kiln.product.domain.model.Product;
import com.skyflux.kiln.product.domain.model.ProductId;
import com.skyflux.kiln.tenant.api.TenantContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductUseCaseTest {

    static final UUID TENANT_ID = UUID.randomUUID();
    static final Money PRICE = Money.of("10.00", "CNY");

    static Product makeProduct() {
        return Product.create(TENANT_ID, "SKU-001", "Widget", null, PRICE);
    }

    // ─── CreateProductService ────────────────────────────────────────────────

    @Nested
    @ExtendWith(MockitoExtension.class)
    class CreateProductServiceTest {

        @Mock
        ProductRepository repo;

        @InjectMocks
        CreateProductService service;

        @Test
        void shouldCreateProductWithTenantFromContext() {
            var cmd = new CreateProductUseCase.Command("SKU-001", "Widget", null, PRICE);
            Product[] created = {null};

            ScopedValue.where(TenantContext.CURRENT, TENANT_ID).run(() ->
                    created[0] = service.execute(cmd));

            assertThat(created[0]).isNotNull();
            assertThat(created[0].tenantId()).isEqualTo(TENANT_ID);
            assertThat(created[0].code()).isEqualTo("SKU-001");
            assertThat(created[0].status()).isEqualTo("ACTIVE");
            verify(repo).save(created[0]);
        }

        @Test
        void shouldRejectNullCommand() {
            assertThatNullPointerException().isThrownBy(() ->
                    ScopedValue.where(TenantContext.CURRENT, TENANT_ID).run(() ->
                            service.execute(null)));
        }
    }

    // ─── GetProductService ───────────────────────────────────────────────────

    @Nested
    @ExtendWith(MockitoExtension.class)
    class GetProductServiceTest {

        @Mock
        ProductRepository repo;

        @InjectMocks
        GetProductService service;

        @Test
        void shouldReturnProductWhenFound() {
            Product p = makeProduct();
            when(repo.findById(p.id())).thenReturn(Optional.of(p));
            assertThat(service.execute(p.id())).isSameAs(p);
        }

        @Test
        void shouldThrowNotFoundWhenMissing() {
            ProductId id = ProductId.newId();
            when(repo.findById(id)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.execute(id))
                    .isInstanceOf(AppException.class)
                    .extracting(ex -> ((AppException) ex).appCode())
                    .isEqualTo(AppCode.NOT_FOUND);
        }

        @Test
        void shouldRejectNullId() {
            assertThatNullPointerException().isThrownBy(() -> service.execute(null));
        }
    }

    // ─── UpdateProductService ────────────────────────────────────────────────

    @Nested
    @ExtendWith(MockitoExtension.class)
    class UpdateProductServiceTest {

        @Mock
        ProductRepository repo;

        @InjectMocks
        UpdateProductService service;

        @Test
        void shouldUpdateAndSave() {
            Product p = makeProduct();
            when(repo.findById(p.id())).thenReturn(Optional.of(p));

            Money newPrice = Money.of("20.00", "CNY");
            var cmd = new UpdateProductUseCase.Command(p.id(), "New Name", "desc", newPrice);
            Product result = service.execute(cmd);

            assertThat(result.name()).isEqualTo("New Name");
            assertThat(result.price().amount()).isEqualByComparingTo("20.00");
            verify(repo).save(result);
        }

        @Test
        void shouldThrowNotFoundWhenMissing() {
            ProductId id = ProductId.newId();
            when(repo.findById(id)).thenReturn(Optional.empty());
            var cmd = new UpdateProductUseCase.Command(id, "Name", null, PRICE);
            assertThatThrownBy(() -> service.execute(cmd))
                    .isInstanceOf(AppException.class)
                    .extracting(ex -> ((AppException) ex).appCode())
                    .isEqualTo(AppCode.NOT_FOUND);
        }
    }

    // ─── DeleteProductService ────────────────────────────────────────────────

    @Nested
    @ExtendWith(MockitoExtension.class)
    class DeleteProductServiceTest {

        @Mock
        ProductRepository repo;

        @InjectMocks
        DeleteProductService service;

        @Test
        void shouldDeactivateAndSave() {
            Product p = makeProduct();
            when(repo.findById(p.id())).thenReturn(Optional.of(p));

            service.execute(p.id());

            verify(repo).save(argThat(saved -> "INACTIVE".equals(saved.status())));
        }

        @Test
        void shouldThrowNotFoundWhenMissing() {
            ProductId id = ProductId.newId();
            when(repo.findById(id)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.execute(id))
                    .isInstanceOf(AppException.class)
                    .extracting(ex -> ((AppException) ex).appCode())
                    .isEqualTo(AppCode.NOT_FOUND);
        }
    }

    // ─── ListProductsService ─────────────────────────────────────────────────

    @Nested
    @ExtendWith(MockitoExtension.class)
    class ListProductsServiceTest {

        @Mock
        ProductRepository repo;

        @InjectMocks
        ListProductsService service;

        @Test
        void shouldDelegateToRepository() {
            PageQuery query = new PageQuery(1, 20, null);
            Product p = makeProduct();
            PageResult<Product> expected = PageResult.of(List.of(p), 1L, query);
            when(repo.listActive(query)).thenReturn(expected);

            PageResult<Product> result = service.execute(query);

            assertThat(result).isSameAs(expected);
        }
    }
}
