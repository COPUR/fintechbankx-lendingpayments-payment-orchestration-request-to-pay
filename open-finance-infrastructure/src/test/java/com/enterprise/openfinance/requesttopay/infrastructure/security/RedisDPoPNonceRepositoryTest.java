package com.enterprise.openfinance.requesttopay.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class RedisDPoPNonceRepositoryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisDPoPNonceRepository repository;

    @BeforeEach
    void setUp() {
        repository = new RedisDPoPNonceRepository(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void saveJtiIfAbsent_shouldReturnTrueWhenRedisStoresKey() {
        when(valueOperations.setIfAbsent(
                eq("rtp:dpop:jti:jti-1"),
                eq("true"),
                eq(Duration.ofSeconds(300))
        )).thenReturn(Boolean.TRUE);

        boolean saved = repository.saveJtiIfAbsent("jti-1", 300);

        assertThat(saved).isTrue();
    }

    @Test
    void saveJtiIfAbsent_shouldReturnFalseWhenRedisDoesNotStoreKey() {
        when(valueOperations.setIfAbsent(
                eq("rtp:dpop:jti:jti-1"),
                eq("true"),
                eq(Duration.ofSeconds(300))
        )).thenReturn(Boolean.FALSE);

        boolean saved = repository.saveJtiIfAbsent("jti-1", 300);

        assertThat(saved).isFalse();
    }
}
