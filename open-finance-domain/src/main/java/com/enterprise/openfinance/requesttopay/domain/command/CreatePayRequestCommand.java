package com.enterprise.openfinance.requesttopay.domain.command;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/**
 * @param psuId          opaque PSU reference (letters, digits and hyphens, at most 64 characters),
 *                       never a name, e-mail address or account number
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

    /** Same format as the platform's customer_id: an opaque reference, no personal data. */
    public static final String PSU_ID_PATTERN = "^[A-Za-z0-9-]{1,64}$";
    private static final java.util.regex.Pattern PSU_ID = java.util.regex.Pattern.compile(PSU_ID_PATTERN);

    public CreatePayRequestCommand {
        tppId = requireNotBlank(tppId, "tppId");
        psuId = requireNotBlank(psuId, "psuId");
        if (!PSU_ID.matcher(psuId).matches()) {
            throw new IllegalArgumentException("psuId must be 1 to 64 letters, digits or hyphens");
        }
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
