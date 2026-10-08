package com.enterprise.openfinance.requesttopay.domain.exception;

/**
 * The calling TPP may not read or decide this pay request: the id is unknown, or
 * another TPP created it. Both get the same public message (and the same 403), so
 * pay request ids cannot be probed. {@link #reason()} is for the service log only.
 */
public class PayRequestAccessDeniedException extends RuntimeException {

    public static final String MESSAGE = "Pay request not found or not authorised";

    public enum Reason {
        NOT_FOUND,
        OTHER_TPP
    }

    private final Reason reason;

    public PayRequestAccessDeniedException(Reason reason) {
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
