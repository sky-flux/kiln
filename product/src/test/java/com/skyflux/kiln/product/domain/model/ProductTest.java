package com.skyflux.kiln.product.domain.model;

import com.skyflux.kiln.common.money.Money;
import com.skyflux.kiln.common.util.Ids;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ProductTest {

    @Test
    void shouldCreateWithActiveStatus() {
        Product p = Product.create(Ids.next(), "SKU-001", "Widget", null, Money.of("99.99", "CNY"));
        assertThat(p.status()).isEqualTo("ACTIVE");
        assertThat(p.id().value().version()).isEqualTo(7);
    }

    @Test
    void shouldDeactivate() {
        Product p = Product.create(Ids.next(), "SKU-001", "Widget", null, Money.of("10.00", "CNY"));
        assertThat(p.deactivate().status()).isEqualTo("INACTIVE");
    }

    @Test
    void shouldRejectDoubleDeactivate() {
        Product p = Product.create(Ids.next(), "X", "X", null, Money.of("1.00", "CNY")).deactivate();
        assertThatIllegalStateException().isThrownBy(p::deactivate);
    }

    @Test
    void shouldRejectNegativePrice() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                Product.create(Ids.next(), "X", "X", null, Money.of("-1.00", "CNY")));
    }

    @Test
    void shouldUpdateDetails() {
        Product p = Product.create(Ids.next(), "SKU-001", "Widget", null, Money.of("10.00", "CNY"));
        Product updated = p.updateDetails("New", "desc", Money.of("20.00", "CNY"));
        assertThat(updated.name()).isEqualTo("New");
        assertThat(updated.price().amount()).isEqualByComparingTo("20.00");
    }
}
