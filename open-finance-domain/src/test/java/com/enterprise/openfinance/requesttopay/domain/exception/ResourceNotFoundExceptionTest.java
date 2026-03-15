package com.enterprise.openfinance.requesttopay.domain.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceNotFoundExceptionTest {

    @Test
    void shouldExposeMessage() {
        ResourceNotFoundException exception = new ResourceNotFoundException("missing");

        assertThat(exception.getMessage()).isEqualTo("missing");
    }
}
