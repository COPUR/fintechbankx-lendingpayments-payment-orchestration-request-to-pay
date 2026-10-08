package com.enterprise.openfinance.requesttopay.infrastructure.rest.dto;

import com.enterprise.openfinance.requesttopay.domain.command.CreatePayRequestCommand;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record PayRequestRequest(
        @JsonProperty("Data") @NotNull @Valid Data data
) {

    public CreatePayRequestCommand toCommand(String tppId, String interactionId) {
        return toCommand(tppId, interactionId, null);
    }

    public CreatePayRequestCommand toCommand(String tppId, String interactionId, String idempotencyKey) {
        if (data == null || data.instructedAmount() == null || data.instructedAmount().amount() == null) {
            throw new IllegalArgumentException("Data.InstructedAmount.Amount is required");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(data.instructedAmount().amount().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Data.InstructedAmount.Amount must be a decimal number");
        }
        return new CreatePayRequestCommand(
                tppId,
                data.psuId(),
                data.creditorName(),
                amount,
                data.instructedAmount().currency(),
                Instant.now(),
                interactionId,
                idempotencyKey
        );
    }

    public record Data(
            /* Opaque PSU reference (customer_id format), never personal data. */
            @JsonProperty("PsuId") @NotBlank @Pattern(regexp = CreatePayRequestCommand.PSU_ID_PATTERN) String psuId,
            @JsonProperty("CreditorName") @NotBlank @Size(max = 140) String creditorName,
            @JsonProperty("InstructedAmount") @NotNull @Valid Amount instructedAmount
    ) {
    }

    public record Amount(
            @JsonProperty("Amount") @NotBlank @Pattern(regexp = "^[0-9]{1,15}(\\.[0-9]{1,4})?$") String amount,
            @JsonProperty("Currency") @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String currency
    ) {
    }
}
