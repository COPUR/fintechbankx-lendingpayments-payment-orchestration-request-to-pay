package com.enterprise.openfinance.requesttopay.infrastructure.config;

import com.enterprise.openfinance.requesttopay.domain.model.PayRequestSettings;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class RequestToPayConfigurationTest {

    private final RequestToPayConfiguration configuration = new RequestToPayConfiguration();

    @Test
    void shouldProvideUtcClock() {
        assertThat(configuration.payRequestClock().getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void shouldBuildPayRequestSettingsFromProperties() {
        RequestToPayCacheProperties properties = new RequestToPayCacheProperties();
        properties.setTtl(Duration.ofSeconds(120));

        RequestToPayIdempotencyProperties idempotency = new RequestToPayIdempotencyProperties();
        idempotency.setTtl(Duration.ofHours(12));

        PayRequestSettings settings = configuration.payRequestSettings(properties, idempotency);

        assertThat(settings.cacheTtl()).isEqualTo(Duration.ofSeconds(120));
        assertThat(settings.idempotencyTtl()).isEqualTo(Duration.ofHours(12));
        assertThat(new RequestToPayIdempotencyProperties().getTtl()).isEqualTo(Duration.ofHours(24));
        assertThat(configuration.payRequestCache()).isNotNull();
    }

    @Test
    void shouldGenerateConsentIdsWithExpectedPrefix() {
        Supplier<String> generator = configuration.payRequestConsentIdGenerator();

        String consentId = generator.get();

        assertThat(consentId).startsWith("CONS-RTP-");
    }
}
