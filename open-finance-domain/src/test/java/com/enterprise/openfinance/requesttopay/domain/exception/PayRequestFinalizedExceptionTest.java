package com.enterprise.openfinance.requesttopay.domain.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PayRequestFinalizedExceptionTest {

    @Test
    void shouldExposeMessage() {
        PayRequestFinalizedException exception = new PayRequestFinalizedException("Already finalized");

        assertThat(exception.getMessage()).contains("Already finalized");
    }
}
