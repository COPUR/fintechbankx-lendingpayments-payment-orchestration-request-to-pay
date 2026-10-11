package com.enterprise.openfinance.requesttopay.domain.model.valueobject;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;

/**
 * A positive amount in an ISO 4217 currency, held at exactly the currency's
 * minor unit (AED 2, BHD 3, JPY 0). An amount with more significant decimals
 * than the currency allows is rejected rather than rounded.
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency is required");
        }
        currency = currency.trim().toUpperCase(Locale.ROOT);
        int minorDigits = minorDigits(currency);
        if (amount.stripTrailingZeros().scale() > minorDigits) {
            throw new IllegalArgumentException(
                    "amount " + amount.toPlainString() + ": " + currency + " allows " + minorDigits + " decimal places");
        }
        amount = amount.setScale(minorDigits);
    }

    private static int minorDigits(String code) {
        try {
            int digits = code.length() == 3 ? Currency.getInstance(code).getDefaultFractionDigits() : -1;
            if (digits >= 0) {
                return digits;
            }
        } catch (IllegalArgumentException ignored) {
            // not an ISO 4217 code
        }
        throw new IllegalArgumentException("currency must be an ISO 4217 code: " + code);
    }
}
