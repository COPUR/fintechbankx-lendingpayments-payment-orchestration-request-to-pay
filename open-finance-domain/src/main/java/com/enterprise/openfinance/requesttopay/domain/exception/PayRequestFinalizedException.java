package com.enterprise.openfinance.requesttopay.domain.exception;

public class PayRequestFinalizedException extends RuntimeException {

    public PayRequestFinalizedException(String message) {
        super(message);
    }
}
