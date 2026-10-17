package com.enterprise.openfinance.requesttopay.domain.model.valueobject;

/**
 * Who decided a pay request and, optionally, why. Today the deciding client is
 * the TPP that created the request (monolith parity; the payments owner has
 * not yet decided whether acceptance must come from the debtor's side).
 *
 * @param actorClientId the OAuth2 client that made the decision (azp)
 * @param reason        optional free text, at most {@value #MAX_REASON} characters
 */
public record DecisionBy(String actorClientId, String reason) {

    public static final int MAX_REASON = 256;
    private static final int MAX_CLIENT_ID = 128;

    public DecisionBy {
        if (actorClientId == null || actorClientId.isBlank()) {
            throw new IllegalArgumentException("actorClientId is required");
        }
        actorClientId = actorClientId.trim();
        if (actorClientId.length() > MAX_CLIENT_ID) {
            throw new IllegalArgumentException("actorClientId is longer than " + MAX_CLIENT_ID + " characters");
        }
        reason = reason == null || reason.isBlank() ? null : reason.trim();
        if (reason != null && reason.length() > MAX_REASON) {
            throw new IllegalArgumentException("reason is longer than " + MAX_REASON + " characters");
        }
    }
}
