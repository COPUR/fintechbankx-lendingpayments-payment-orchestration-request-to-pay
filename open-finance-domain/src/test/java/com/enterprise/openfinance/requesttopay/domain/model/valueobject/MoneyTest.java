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
}
