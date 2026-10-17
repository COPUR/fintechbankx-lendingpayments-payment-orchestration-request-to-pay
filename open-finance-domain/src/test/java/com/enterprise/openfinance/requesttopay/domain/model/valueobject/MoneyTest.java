package com.enterprise.openfinance.requesttopay.domain.model.valueobject;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void shouldCreateWhenAmountAndCurrencyAreValid() {
        Money money = new Money(new BigDecimal("500.00"), "AED");

        assertThat(money.amount()).isEqualByComparingTo("500.00");
        assertThat(money.currency()).isEqualTo("AED");
    }

    @Test
    void shouldRejectNullAmount() {
        assertThatThrownBy(() -> new Money(null, "AED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be positive");
    }

    @Test
    void shouldRejectZeroOrNegativeAmount() {
        assertThatThrownBy(() -> new Money(BigDecimal.ZERO, "AED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be positive");

        assertThatThrownBy(() -> new Money(new BigDecimal("-1.00"), "AED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount must be positive");
    }

    @Test
    void shouldRejectMissingCurrency() {
        assertThatThrownBy(() -> new Money(new BigDecimal("10.00"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Currency is required");

        assertThatThrownBy(() -> new Money(new BigDecimal("10.00"), "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Currency is required");
    }

    @Test
    void normalisesToTheCurrencyMinorUnit() {
        assertThat(new Money(new BigDecimal("500"), "aed").amount()).isEqualByComparingTo("500.00")
                .hasToString("500.00");
        assertThat(new Money(new BigDecimal("12.3450"), "BHD").amount()).hasToString("12.345");
        assertThat(new Money(new BigDecimal("100"), "JPY").amount()).hasToString("100");
        assertThat(new Money(new BigDecimal("500.1"), "AED")).isEqualTo(new Money(new BigDecimal("500.10"), "AED"));
    }

    @Test
    void rejectsMoreDecimalsThanTheCurrencyAllows() {
        assertThatThrownBy(() -> new Money(new BigDecimal("0.005"), "AED"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("AED allows 2 decimal places");
        assertThatThrownBy(() -> new Money(new BigDecimal("1.5"), "JPY"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JPY allows 0 decimal places");
    }

    @Test
    void rejectsCodesThatAreNotIso4217Currencies() {
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, "XXQ"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ISO 4217");
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, "XXX"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ISO 4217");
    }
}
