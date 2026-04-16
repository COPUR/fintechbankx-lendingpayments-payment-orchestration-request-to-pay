package com.enterprise.openfinance.requesttopay.infrastructure.cache;

public interface IdempotencyKeyRepository {
    boolean saveIfAbsent(String idempotencyKey, String payloadHash, long ttlSeconds);
}
