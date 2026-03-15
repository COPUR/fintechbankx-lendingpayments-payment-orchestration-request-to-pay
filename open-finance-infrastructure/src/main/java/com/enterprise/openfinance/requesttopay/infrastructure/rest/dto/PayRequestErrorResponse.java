package com.enterprise.openfinance.requesttopay.infrastructure.rest.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public record PayRequestErrorResponse(
        String code,
        String message,
        String interactionId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant timestamp
) {

    public static PayRequestErrorResponse of(String code, String message, String interactionId) {
        return new PayRequestErrorResponse(code, message, interactionId, Instant.now());
    }
}
