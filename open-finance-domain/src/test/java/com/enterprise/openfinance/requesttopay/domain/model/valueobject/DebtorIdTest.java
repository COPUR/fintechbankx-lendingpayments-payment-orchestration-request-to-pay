package com.enterprise.openfinance.requesttopay.domain.model.valueobject;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DebtorIdTest {

    @Test
    void shouldCreateWhenValueIsPresent() {
        DebtorId debtorId = new DebtorId("DEBT-001");

        assertThat(debtorId.value()).isEqualTo("DEBT-001");
    }

    @Test
    void shouldRejectNullValue() {
        assertThatThrownBy(() -> new DebtorId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DebtorId value cannot be blank");
    }

    @Test
    void shouldRejectBlankValue() {
        assertThatThrownBy(() -> new DebtorId("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("DebtorId value cannot be blank");
    }
}
