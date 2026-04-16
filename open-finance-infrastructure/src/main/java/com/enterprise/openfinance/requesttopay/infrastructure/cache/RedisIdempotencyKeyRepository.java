package com.enterprise.openfinance.requesttopay.infrastructure.cache;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

@Repository
public class RedisIdempotencyKeyRepository implements IdempotencyKeyRepository {

    private final StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "idempotency:";

    public RedisIdempotencyKeyRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean saveIfAbsent(String idempotencyKey, String payloadHash, long ttlSeconds) {
        String key = KEY_PREFIX + idempotencyKey;
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, payloadHash, ttlSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public String findPayloadHash(String idempotencyKey) {
        return redisTemplate.opsForValue().get(KEY_PREFIX + idempotencyKey);
    }
}
