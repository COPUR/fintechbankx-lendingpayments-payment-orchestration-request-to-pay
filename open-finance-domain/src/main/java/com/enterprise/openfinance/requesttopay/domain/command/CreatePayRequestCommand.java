package com.enterprise.openfinance.requesttopay.domain.command;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/**
 * @param idempotencyKey optional x-idempotency-key; a retry with the same key
 *                       and the same {@link #fingerprint()} returns the first result
 */
public record CreatePayRequestCommand(
        String tppId,
        String psuId,
        String creditorName,
        BigDecimal amount,
        String currency,
        Instant requestedAt,
        String interactionId,
        String idempotencyKey
) {

    public CreatePayRequestCommand {
        tppId = requireNotBlank(tppId, "tppId");
        psuId = requireNotBlank(psuId, "psuId");
        creditorName = requireNotBlank(creditorName, "creditorName");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        currency = requireNotBlank(currency, "currency").toUpperCase(Locale.ROOT);
        if (requestedAt == null) {
            throw new IllegalArgumentException("requestedAt is required");
        }
        interactionId = requireNotBlank(interactionId, "interactionId");
        idempotencyKey = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
    }

    public CreatePayRequestCommand(String tppId, String psuId, String creditorName, BigDecimal amount,
                                   String currency, Instant requestedAt, String interactionId) {
        this(tppId, psuId, creditorName, amount, currency, requestedAt, interactionId, null);
    }

    /**
     * SHA-256 (hex) of the business payload: TPP, debtor, creditor, amount
     * (scale-normalised) and currency. Request time and interaction id are
     * excluded so that a retry of the same request matches.
     */
    public String fingerprint() {
        String canonical = String.join("\u001f",
                tppId, psuId, creditorName, amount.stripTrailingZeros().toPlainString(), currency);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
