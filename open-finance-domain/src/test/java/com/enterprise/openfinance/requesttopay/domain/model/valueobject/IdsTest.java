package com.enterprise.openfinance.requesttopay.domain.model.valueobject;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdsTest {

    @Test
    void shouldCreateValidCreditorId() {
        CreditorId id = new CreditorId("cred-123");
        assertThat(id.value()).isEqualTo("cred-123");
    }

    @Test
    void shouldRejectBlankCreditorId() {
        assertThatThrownBy(() -> new CreditorId("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldCreateValidDebtorId() {
        DebtorId id = new DebtorId("debt-123");
        assertThat(id.value()).isEqualTo("debt-123");
    }

    @Test
    void shouldRejectBlankDebtorId() {
        assertThatThrownBy(() -> new DebtorId(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}