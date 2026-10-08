package com.enterprise.openfinance.requesttopay.domain.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainExceptionsTest {

    @Test
    void exceptionsCarryTheirMessage() {
        assertThat(new PayRequestAccessDeniedException("other TPP")).hasMessage("other TPP");
        assertThat(new IdempotencyKeyConflictException("reused key")).hasMessage("reused key");
        assertThat(new PayRequestFinalizedException("done")).isInstanceOf(IllegalStateException.class);
    }
}
