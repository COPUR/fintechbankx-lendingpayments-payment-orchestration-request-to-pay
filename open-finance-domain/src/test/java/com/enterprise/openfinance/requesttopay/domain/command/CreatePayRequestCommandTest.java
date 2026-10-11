package com.enterprise.openfinance.requesttopay.domain.command;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreatePayRequestCommandTest {

    @Test
    void shouldNormalizeFields() {
        CreatePayRequestCommand command = new CreatePayRequestCommand(
                "  TPP-001 ",
                "PSU-123",
                " Utilities Co ",
                new BigDecimal("500.00"),
                "AED",
                Instant.parse("2026-02-10T10:00:00Z"),
                "ix-request-to-pay-1"
        );

        assertThat(command.tppId()).isEqualTo("TPP-001");
        assertThat(command.psuId()).isEqualTo("PSU-123");
        assertThat(command.creditorName()).isEqualTo("Utilities Co");
        assertThat(command.currency()).isEqualTo("AED");
    }

    @Test
    void shouldRejectInvalidFields() {
        assertThatThrownBy(() -> new CreatePayRequestCommand(" ", "PSU", "Creditor", new BigDecimal("1.00"), "AED",
                Instant.parse("2026-02-10T10:00:00Z"), "ix"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tppId");

        assertThatThrownBy(() -> new CreatePayRequestCommand("TPP", " ", "Creditor", new BigDecimal("1.00"), "AED",
                Instant.parse("2026-02-10T10:00:00Z"), "ix"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("psuId");

        assertThatThrownBy(() -> new CreatePayRequestCommand("TPP", "PSU", " ", new BigDecimal("1.00"), "AED",
                Instant.parse("2026-02-10T10:00:00Z"), "ix"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("creditorName");

        assertThatThrownBy(() -> new CreatePayRequestCommand("TPP", "PSU", "Creditor", new BigDecimal("0.00"), "AED",
                Instant.parse("2026-02-10T10:00:00Z"), "ix"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");

        assertThatThrownBy(() -> new CreatePayRequestCommand("TPP", "PSU", "Creditor", new BigDecimal("1.00"), " ",
                Instant.parse("2026-02-10T10:00:00Z"), "ix"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");

        assertThatThrownBy(() -> new CreatePayRequestCommand("TPP", "PSU", "Creditor", new BigDecimal("1.00"), "AED",
                null, "ix"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requestedAt");

        assertThatThrownBy(() -> new CreatePayRequestCommand("TPP", "PSU", "Creditor", new BigDecimal("1.00"), "AED",
                Instant.parse("2026-02-10T10:00:00Z"), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("interactionId");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"PSU 001", "psu@example.com", "PSU_001", "PSU/1", "PSÜ-1"})
    void psuIdIsAnOpaqueReferenceOfLettersDigitsAndHyphens(String psuId) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new CreatePayRequestCommand("TPP-001", psuId,
                        "Utilities Co", new java.math.BigDecimal("1.00"), "AED", java.time.Instant.now(), "ix"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("psuId");
    }

    @Test
    void psuIdIsAtMostSixtyFourCharacters() {
        org.assertj.core.api.Assertions.assertThat(new CreatePayRequestCommand("TPP-001", "a".repeat(64),
                "Utilities Co", new java.math.BigDecimal("1.00"), "AED", java.time.Instant.now(), "ix").psuId())
                .hasSize(64);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new CreatePayRequestCommand("TPP-001", "a".repeat(65),
                        "Utilities Co", new java.math.BigDecimal("1.00"), "AED", java.time.Instant.now(), "ix"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("psuId");
    }
}
