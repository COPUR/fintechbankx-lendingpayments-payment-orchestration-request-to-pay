package com.enterprise.openfinance.requesttopay.infrastructure.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class RedisIdempotencyKeyRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisIdempotencyKeyRepository repository;

    @BeforeEach
    void setUp() {
        repository = new RedisIdempotencyKeyRepository(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void saveIfAbsent_shouldReturnTrueWhenRedisStoresKey() {
        when(valueOperations.setIfAbsent(
                eq("rtp:idemp:key-1"),
                eq("payload-hash"),
                eq(Duration.ofSeconds(60))
        )).thenReturn(Boolean.TRUE);

        boolean saved = repository.saveIfAbsent("key-1", "payload-hash", 60);

        assertThat(saved).isTrue();
    }

    @Test
    void saveIfAbsent_shouldReturnFalseWhenRedisDoesNotStoreKey() {
        when(valueOperations.setIfAbsent(
                eq("rtp:idemp:key-1"),
                eq("payload-hash"),
                eq(Duration.ofSeconds(60))
        )).thenReturn(Boolean.FALSE);

        boolean saved = repository.saveIfAbsent("key-1", "payload-hash", 60);

        assertThat(saved).isFalse();
    }
}
