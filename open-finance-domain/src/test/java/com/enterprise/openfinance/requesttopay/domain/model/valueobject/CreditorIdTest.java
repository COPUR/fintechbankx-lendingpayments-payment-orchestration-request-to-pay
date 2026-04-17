package com.enterprise.openfinance.requesttopay.domain.model.valueobject;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreditorIdTest {

    @Test
    void shouldCreateWhenValueIsPresent() {
        CreditorId creditorId = new CreditorId("CRED-001");

        assertThat(creditorId.value()).isEqualTo("CRED-001");
    }

    @Test
    void shouldRejectNullValue() {
        assertThatThrownBy(() -> new CreditorId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CreditorId value cannot be blank");
    }

    @Test
    void shouldRejectBlankValue() {
        assertThatThrownBy(() -> new CreditorId("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CreditorId value cannot be blank");
    }
}
