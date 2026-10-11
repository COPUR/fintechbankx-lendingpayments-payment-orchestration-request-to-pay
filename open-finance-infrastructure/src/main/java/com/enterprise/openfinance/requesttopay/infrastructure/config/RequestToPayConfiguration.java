package com.enterprise.openfinance.requesttopay.infrastructure.config;

import com.enterprise.openfinance.requesttopay.domain.model.PayRequestSettings;
import com.enterprise.openfinance.requesttopay.infrastructure.cache.InMemoryPayRequestCacheAdapter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;

@Configuration
@EnableConfigurationProperties({RequestToPayCacheProperties.class, RequestToPayIdempotencyProperties.class})
public class RequestToPayConfiguration {

    @Bean
    public Clock payRequestClock() {
        return Clock.systemUTC();
    }

    @Bean
    public PayRequestSettings payRequestSettings(RequestToPayCacheProperties cache,
                                                 RequestToPayIdempotencyProperties idempotency) {
        return new PayRequestSettings(cache.getTtl(), idempotency.getTtl());
    }

    @Bean
    public Supplier<String> payRequestConsentIdGenerator() {
        // CONS-RTP2-: distinct from the monolith's CONS-RTP- ids, so the gateway routes follow-ups by prefix (runbook).
        return () -> "CONS-RTP2-" + UUID.randomUUID();
    }

    /**
     * Per-replica status cache. Decisions refresh the deciding replica; other
     * replicas may serve the previous status for at most the cache TTL.
     */
    @Bean
    public InMemoryPayRequestCacheAdapter payRequestCache() {
        return new InMemoryPayRequestCacheAdapter();
    }
}
