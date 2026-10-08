package com.enterprise.openfinance.requesttopay.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "openfinance.requesttopay.idempotency")
public class RequestToPayIdempotencyProperties {

    /** How long an x-idempotency-key is remembered per TPP. */
    private Duration ttl = Duration.ofHours(24);

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }
}
