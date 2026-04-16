package com.enterprise.openfinance.requesttopay.infrastructure.cache;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import java.time.Duration;

@Repository
public class RedisIdempotencyKeyRepository implements IdempotencyKeyRepository {

    private final StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "rtp:idemp:";

    public RedisIdempotencyKeyRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean saveIfAbsent(String idempotencyKey, String payloadHash, long ttlSeconds) {
        String key = KEY_PREFIX + idempotencyKey;
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, payloadHash, Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(success);
    }
}
