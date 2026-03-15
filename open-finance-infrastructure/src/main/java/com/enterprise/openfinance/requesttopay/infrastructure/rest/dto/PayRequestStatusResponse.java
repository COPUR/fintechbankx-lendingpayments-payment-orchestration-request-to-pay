package com.enterprise.openfinance.requesttopay.infrastructure.rest.dto;

import com.enterprise.openfinance.requesttopay.domain.model.PayRequestResult;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PayRequestStatusResponse(
        @JsonProperty("Data") Data data,
        @JsonProperty("Links") Links links
) {

    public static PayRequestStatusResponse from(PayRequestResult result, String self) {
        return new PayRequestStatusResponse(
                new Data(
                        result.request().consentId(),
                        result.request().status().apiValue(),
                        result.request().paymentIdOptional().orElse(null)
                ),
                new Links(self)
        );
    }

    public record Data(
            @JsonProperty("ConsentId") String consentId,
            @JsonProperty("Status") String status,
            @JsonProperty("PaymentId") String paymentId
    ) {
    }

    public record Links(
            @JsonProperty("Self") String self
    ) {
    }
}
