package com.enterprise.openfinance.requesttopay.domain.query;

public record GetPayRequestStatusQuery(
        String consentId,
        String tppId,
        String interactionId
) {

    public GetPayRequestStatusQuery {
        consentId = requireNotBlank(consentId, "consentId");
        tppId = requireNotBlank(tppId, "tppId");
        interactionId = requireNotBlank(interactionId, "interactionId");
    }

    private static String requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
