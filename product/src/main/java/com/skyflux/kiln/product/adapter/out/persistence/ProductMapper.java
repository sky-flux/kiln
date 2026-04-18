package com.skyflux.kiln.product.adapter.out.persistence;

import com.skyflux.kiln.common.money.Money;
import com.skyflux.kiln.infra.jooq.generated.tables.records.ProductsRecord;
import com.skyflux.kiln.product.domain.model.Product;
import com.skyflux.kiln.product.domain.model.ProductId;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;

/**
 * Translates between the {@link Product} aggregate and the jOOQ-generated
 * {@link ProductsRecord}. Package-private — only the adapter and tests use it.
 */
@Component
class ProductMapper {

    Product toAggregate(ProductsRecord r) {
        String priceCurrency = r.getPriceCurrency();
        Currency currency = Currency.getInstance(priceCurrency);
        // DB stores NUMERIC(19,4); strip to currency's fraction digits to satisfy Money's scale check.
        BigDecimal priceAmount = r.getPriceAmount().setScale(
                currency.getDefaultFractionDigits(), java.math.RoundingMode.UNNECESSARY);
        Money price = new Money(priceAmount, currency);
        return Product.reconstitute(
                new ProductId(r.getId()),
                r.getTenantId(),
                r.getCode(),
                r.getName(),
                r.getDescription(),
                price,
                r.getStatus());
    }

    ProductsRecord toRecord(Product p) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ProductsRecord r = new ProductsRecord();
        r.setId(p.id().value());
        r.setTenantId(p.tenantId());
        r.setCode(p.code());
        r.setName(p.name());
        r.setDescription(p.description());
        r.setPriceAmount(p.price().amount());
        r.setPriceCurrency(p.price().currency().getCurrencyCode());
        r.setStatus(p.status());
        r.setCreatedAt(now);
        r.setUpdatedAt(now);
        return r;
    }
}
