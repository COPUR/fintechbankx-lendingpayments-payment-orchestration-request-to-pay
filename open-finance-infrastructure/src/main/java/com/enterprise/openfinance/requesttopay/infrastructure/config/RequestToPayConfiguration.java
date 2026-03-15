package com.enterprise.openfinance.requesttopay.infrastructure.config;

import com.enterprise.openfinance.requesttopay.domain.model.PayRequestSettings;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;

@Configuration
@EnableConfigurationProperties(RequestToPayCacheProperties.class)
public class RequestToPayConfiguration {

    @Bean
    public Clock payRequestClock() {
        return Clock.systemUTC();
    }

    @Bean
    public PayRequestSettings payRequestSettings(RequestToPayCacheProperties properties) {
        return new PayRequestSettings(properties.getTtl());
    }

    @Bean
    public Supplier<String> payRequestConsentIdGenerator() {
        return () -> "CONS-RTP-" + UUID.randomUUID();
    }
}
