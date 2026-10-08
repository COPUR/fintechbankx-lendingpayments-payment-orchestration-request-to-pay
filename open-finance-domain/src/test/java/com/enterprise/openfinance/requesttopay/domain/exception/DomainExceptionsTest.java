package com.enterprise.openfinance.requesttopay.domain.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainExceptionsTest {

    @Test
    void exceptionsCarryTheirMessage() {
        assertThat(new PayRequestAccessDeniedException(PayRequestAccessDeniedException.Reason.OTHER_TPP))
                .hasMessage("Pay request not found or not authorised")
                .extracting(PayRequestAccessDeniedException::reason)
                .isEqualTo(PayRequestAccessDeniedException.Reason.OTHER_TPP);
        assertThat(new IdempotencyKeyConflictException("reused key")).hasMessage("reused key");
        assertThat(new PayRequestFinalizedException("done")).isInstanceOf(IllegalStateException.class);
    }
}
