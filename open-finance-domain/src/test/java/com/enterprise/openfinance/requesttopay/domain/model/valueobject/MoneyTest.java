package com.enterprise.openfinance.requesttopay.domain.model.valueobject;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void shouldCreateValidMoney() {
        Money money = new Money(new BigDecimal("100.50"), "USD");
        assertThat(money.amount()).isEqualTo(new BigDecimal("100.50"));
        assertThat(money.currency()).isEqualTo("USD");
    }

    @Test
    void shouldRejectNegativeOrZeroAmount() {
        assertThatThrownBy(() -> new Money(BigDecimal.ZERO, "USD"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new Money(new BigDecimal("-10.00"), "USD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullOrBlankCurrency() {
        assertThatThrownBy(() -> new Money(new BigDecimal("10.00"), null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new Money(new BigDecimal("10.00"), "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}