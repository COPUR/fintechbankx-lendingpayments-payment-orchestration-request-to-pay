package com.enterprise.openfinance.requesttopay.domain.model;

public record PayRequestResult(
        PayRequest request,
        boolean cacheHit,
        boolean idempotentReplay
) {

    public PayRequestResult {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
    }

    public PayRequestResult(PayRequest request, boolean cacheHit) {
        this(request, cacheHit, false);
    }

    public static PayRequestResult replayOf(PayRequest request) {
        return new PayRequestResult(request, false, true);
    }

    public PayRequestStatus status() {
        return request.status();
    }

    public PayRequestResult withCacheHit(boolean cacheHitValue) {
        return new PayRequestResult(request, cacheHitValue, idempotentReplay);
    }
}
