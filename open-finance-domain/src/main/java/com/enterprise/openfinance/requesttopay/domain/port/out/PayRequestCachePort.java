package com.enterprise.openfinance.requesttopay.domain.port.out;

import com.enterprise.openfinance.requesttopay.domain.model.PayRequestResult;

import java.time.Instant;
import java.util.Optional;

public interface PayRequestCachePort {
    Optional<PayRequestResult> getStatus(String key, Instant now);

    void putStatus(String key, PayRequestResult result, Instant expiresAt);
}
