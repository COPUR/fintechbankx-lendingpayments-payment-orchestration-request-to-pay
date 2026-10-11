package com.enterprise.openfinance.requesttopay.domain.exception;

/**
 * A decision (accept or reject) was attempted on a pay request that is
 * already Consumed or Rejected. Extends IllegalStateException because it is
 * a rejected state transition.
 */
public class PayRequestFinalizedException extends IllegalStateException {

    public PayRequestFinalizedException(String message) {
        super(message);
    }
}
