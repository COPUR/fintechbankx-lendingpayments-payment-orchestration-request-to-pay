package com.enterprise.openfinance.requesttopay.infrastructure.rest.dto;

import com.enterprise.openfinance.requesttopay.domain.command.CreatePayRequestCommand;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

public record PayRequestRequest(
        @JsonProperty("Data") Data data
) {

    public CreatePayRequestCommand toCommand(String tppId, String interactionId) {
        return new CreatePayRequestCommand(
                tppId,
                data.psuId(),
                data.creditorName(),
                new BigDecimal(data.instructedAmount().amount()),
                data.instructedAmount().currency(),
                Instant.now(),
                interactionId
        );
    }

    public record Data(
            @JsonProperty("PsuId") String psuId,
            @JsonProperty("CreditorName") String creditorName,
            @JsonProperty("InstructedAmount") Amount instructedAmount
    ) {
    }

    public record Amount(
            @JsonProperty("Amount") String amount,
            @JsonProperty("Currency") String currency
    ) {
    }
}
