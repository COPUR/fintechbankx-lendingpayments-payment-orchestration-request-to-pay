package com.enterprise.openfinance.requesttopay.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PayRequestStatusTest {

    @Test
    void shouldExposeApiValues() {
        assertThat(PayRequestStatus.AWAITING_AUTHORISATION.apiValue()).isEqualTo("AwaitingAuthorisation");
        assertThat(PayRequestStatus.REJECTED.apiValue()).isEqualTo("Rejected");
        assertThat(PayRequestStatus.CONSUMED.apiValue()).isEqualTo("Consumed");
    }

    @Test
    void shouldRecognizeTerminalStatuses() {
        assertThat(PayRequestStatus.AWAITING_AUTHORISATION.isFinal()).isFalse();
        assertThat(PayRequestStatus.REJECTED.isFinal()).isTrue();
        assertThat(PayRequestStatus.CONSUMED.isFinal()).isTrue();
    }
}
