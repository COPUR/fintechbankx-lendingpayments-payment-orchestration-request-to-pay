package com.enterprise.openfinance.requesttopay.infrastructure.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class RequestToPayCachePropertiesTest {

    @Test
    void shouldUseDefaultTtl() {
        RequestToPayCacheProperties properties = new RequestToPayCacheProperties();

        assertThat(properties.getTtl()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void shouldAllowOverridingTtl() {
        RequestToPayCacheProperties properties = new RequestToPayCacheProperties();

        properties.setTtl(Duration.ofSeconds(300));

        assertThat(properties.getTtl()).isEqualTo(Duration.ofSeconds(300));
    }
}
