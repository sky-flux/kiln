package com.skyflux.kiln.product.adapter.in.web;

import com.skyflux.kiln.common.money.Money;
import com.skyflux.kiln.common.result.PageQuery;
import com.skyflux.kiln.common.result.PageResult;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.product.application.port.in.CreateProductUseCase;
import com.skyflux.kiln.product.application.port.in.DeleteProductUseCase;
import com.skyflux.kiln.product.application.port.in.GetProductUseCase;
import com.skyflux.kiln.product.application.port.in.ListProductsUseCase;
import com.skyflux.kiln.product.application.port.in.UpdateProductUseCase;
import com.skyflux.kiln.product.domain.model.Product;
import com.skyflux.kiln.product.domain.model.ProductId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @ComponentScan(basePackageClasses = ProductController.class)
    static class BootConfig {}

    @MockitoBean CreateProductUseCase createUseCase;
    @MockitoBean GetProductUseCase getUseCase;
    @MockitoBean UpdateProductUseCase updateUseCase;
    @MockitoBean DeleteProductUseCase deleteUseCase;
    @MockitoBean ListProductsUseCase listUseCase;

    @Autowired MockMvc mvc;

    private static Product sampleProduct() {
        UUID tenantId = Ids.next();
        return Product.reconstitute(
                ProductId.newId(), tenantId, "PROD-001", "Widget",
                "A fine widget", Money.of("9.99", "CNY"), "ACTIVE");
    }

    @Test
    void post_creates_product_and_returns_201() throws Exception {
        Product p = sampleProduct();
        when(createUseCase.execute(any(CreateProductUseCase.Command.class))).thenReturn(p);

        String body = """
                {"code":"PROD-001","name":"Widget","description":"A fine widget",
                 "priceAmount":9.99,"priceCurrency":"CNY"}
                """;

        mvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("PROD-001"))
                .andExpect(jsonPath("$.data.name").value("Widget"));
    }

    @Test
    void get_list_returns_200_with_paginated_products() throws Exception {
        Product p = sampleProduct();
        PageQuery query = new PageQuery(1, 20, null);
        when(listUseCase.execute(any(PageQuery.class)))
                .thenReturn(PageResult.of(List.of(p), 1L, query));

        mvc.perform(get("/api/v1/products")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].code").value("PROD-001"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void get_by_id_returns_200() throws Exception {
        Product p = sampleProduct();
        when(getUseCase.execute(any(ProductId.class))).thenReturn(p);

        mvc.perform(get("/api/v1/products/" + p.id().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("PROD-001"));
    }

    @Test
    void put_updates_product_and_returns_200() throws Exception {
        Product p = sampleProduct();
        when(updateUseCase.execute(any(UpdateProductUseCase.Command.class))).thenReturn(p);

        String body = """
                {"name":"Updated Widget","description":"New desc",
                 "priceAmount":19.99,"priceCurrency":"CNY"}
                """;

        mvc.perform(put("/api/v1/products/" + p.id().value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Widget"));
    }

    @Test
    void delete_returns_204() throws Exception {
        ProductId id = ProductId.newId();

        mvc.perform(delete("/api/v1/products/" + id.value()))
                .andExpect(status().isNoContent());

        verify(deleteUseCase).execute(id);
    }

    @Test
    void post_with_blank_code_returns_400() throws Exception {
        String body = """
                {"code":"","name":"Widget","priceAmount":9.99,"priceCurrency":"CNY"}
                """;
        mvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_with_null_price_returns_400() throws Exception {
        String body = """
                {"code":"PROD-001","name":"Widget","priceCurrency":"CNY"}
                """;
        mvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
