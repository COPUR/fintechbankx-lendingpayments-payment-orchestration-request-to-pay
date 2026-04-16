package com.enterprise.openfinance.requesttopay.infrastructure.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import java.time.Duration;

@Repository
public class RedisDPoPNonceRepository implements DPoPNonceRepository {

    private final StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "rtp:dpop:jti:";

    public RedisDPoPNonceRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean saveJtiIfAbsent(String jti, long ttlSeconds) {
        String key = KEY_PREFIX + jti;
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, "true", Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(success);
    }
}
