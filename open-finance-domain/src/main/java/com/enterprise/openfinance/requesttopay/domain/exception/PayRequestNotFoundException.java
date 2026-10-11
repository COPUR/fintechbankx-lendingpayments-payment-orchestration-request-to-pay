package com.enterprise.openfinance.requesttopay.domain.exception;

/**
 * The calling TPP may not see this pay request: the id is unknown, or another
 * TPP created it. Both get the same answer (404 with one fixed message, ADR-025
 * item 5), so a TPP cannot probe for other TPPs' ids. {@link #reason()} is for
 * the service log only.
 */
public class PayRequestNotFoundException extends RuntimeException {

    public static final String MESSAGE = "Pay request not found";

    public enum Reason {
        NOT_FOUND,
        OTHER_TPP
    }

    private final Reason reason;

    public PayRequestNotFoundException(Reason reason) {
        super(MESSAGE);
        if (reason == null) {
            throw new IllegalArgumentException("reason is required");
        }
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
