package com.enterprise.openfinance.requesttopay.infrastructure.rest.dto;

import com.enterprise.openfinance.requesttopay.domain.model.PayRequestResult;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PayRequestResponse(
        @JsonProperty("Data") Data data,
        @JsonProperty("Links") Links links
) {

    public static PayRequestResponse from(PayRequestResult result, String self) {
        return new PayRequestResponse(
                new Data(
                        result.request().consentId(),
                        result.request().status().apiValue()
                ),
                new Links(self)
        );
    }

    public record Data(
            @JsonProperty("ConsentId") String consentId,
            @JsonProperty("Status") String status
    ) {
    }

    public record Links(
            @JsonProperty("Self") String self
    ) {
    }
}
