package com.skyflux.kiln.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable monetary value object. Amount scale is bounded by the currency's
 * default fraction digits; arithmetic returns new instances.
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.scale() > currency.getDefaultFractionDigits()) {
            throw new IllegalArgumentException(
                    "amount scale %d exceeds %s fraction digits %d"
                            .formatted(amount.scale(), currency,
                                    currency.getDefaultFractionDigits()));
        }
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(
                BigDecimal.ZERO.setScale(
                        currency.getDefaultFractionDigits(), RoundingMode.UNNECESSARY),
                currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money multiply(BigDecimal factor) {
        return new Money(
                amount.multiply(factor).setScale(
                        currency.getDefaultFractionDigits(), RoundingMode.HALF_EVEN),
                currency);
    }

    public boolean isPositive() { return amount.signum() > 0; }
    public boolean isZero() { return amount.signum() == 0; }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot operate on %s and %s".formatted(currency, other.currency));
        }
    }
}
