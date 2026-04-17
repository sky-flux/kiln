package com.skyflux.kiln.common.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");

    @Test
    void compactConstructorRejectsNullAmount() {
        assertThatThrownBy(() -> new Money(null, USD))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void compactConstructorRejectsNullCurrency() {
        assertThatThrownBy(() -> new Money(new BigDecimal("1.00"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void compactConstructorRejectsAmountWithScaleExceedingCurrencyFractionDigits() {
        assertThatThrownBy(() -> Money.of("1.234", "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale");
    }

    @Test
    void addReturnsNewMoneyWithSummedAmounts() {
        Money a = Money.of("1.00", "USD");
        Money b = Money.of("2.50", "USD");

        Money sum = a.add(b);

        assertThat(sum).isEqualTo(Money.of("3.50", "USD"));
    }

    @Test
    void subtractReturnsNewMoneyWithDifferencedAmounts() {
        Money a = Money.of("5.00", "USD");
        Money b = Money.of("1.25", "USD");

        Money diff = a.subtract(b);

        assertThat(diff).isEqualTo(Money.of("3.75", "USD"));
    }

    @Test
    void addAcrossDifferentCurrenciesThrows() {
        Money usd = Money.of("1.00", "USD");
        Money eur = Money.of("1.00", "EUR");

        assertThatThrownBy(() -> usd.add(eur))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void subtractAcrossDifferentCurrenciesThrows() {
        Money usd = Money.of("1.00", "USD");
        Money eur = Money.of("1.00", "EUR");

        assertThatThrownBy(() -> usd.subtract(eur))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void multiplyUsesHalfEvenRoundingAtCurrencyFractionDigits() {
        // 1.00 * 0.125 = 0.125 -> HALF_EVEN at 2 dp -> 0.12 (banker's rounding)
        Money a = Money.of("1.00", "USD");
        Money result = a.multiply(new BigDecimal("0.125"));
        assertThat(result).isEqualTo(Money.of("0.12", "USD"));

        // 1.00 * 0.135 = 0.135 -> HALF_EVEN at 2 dp -> 0.14 (banker's rounding up since 3 is odd)
        Money b = Money.of("1.00", "USD");
        Money result2 = b.multiply(new BigDecimal("0.135"));
        assertThat(result2).isEqualTo(Money.of("0.14", "USD"));
    }

    @Test
    void zeroPlusAmountEqualsAmount() {
        Money result = Money.zero(USD).add(Money.of("1.00", "USD"));
        assertThat(result).isEqualTo(Money.of("1.00", "USD"));
    }

    @Test
    void zeroHasScaleMatchingCurrencyFractionDigits() {
        Money z = Money.zero(USD);
        assertThat(z.amount().scale()).isEqualTo(2);
        assertThat(z.isZero()).isTrue();
    }

    @Test
    void isPositiveClassifiesPositiveAmounts() {
        assertThat(Money.of("0.01", "USD").isPositive()).isTrue();
        assertThat(Money.zero(USD).isPositive()).isFalse();
        assertThat(Money.of("-0.01", "USD").isPositive()).isFalse();
    }

    @Test
    void isZeroClassifiesZeroAmounts() {
        assertThat(Money.zero(USD).isZero()).isTrue();
        assertThat(Money.of("0.00", "USD").isZero()).isTrue();
        assertThat(Money.of("0.01", "USD").isZero()).isFalse();
    }

    @Test
    void sameCurrencyAndAmountAreEqual() {
        assertThat(Money.of("1.00", "USD")).isEqualTo(Money.of("1.00", "USD"));
        assertThat(Money.of("1.00", "USD")).isNotEqualTo(Money.of("1.00", "EUR"));
    }
}
