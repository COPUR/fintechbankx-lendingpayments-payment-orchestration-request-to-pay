package com.enterprise.openfinance.requesttopay.domain.model;

public record PayRequestResult(
        PayRequest request,
        boolean cacheHit
) {

    public PayRequestResult {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
    }

    public PayRequestStatus status() {
        return request.status();
    }

    public PayRequestResult withCacheHit(boolean cacheHitValue) {
        return new PayRequestResult(request, cacheHitValue);
    }
}
