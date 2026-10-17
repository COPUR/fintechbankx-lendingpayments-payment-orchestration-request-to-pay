package com.enterprise.openfinance.requesttopay;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Platform convention (recurring-mandates 2ad4e7f): every meter carries the
 * common tags service (service id), app (service account) and squad (namespace); the
 * platform outbox alerts (OutboxRelayStalled, OutboxSendFailures,
 * OutboxEventsParked) route on squad. Loads the real application.yml; the
 * chart sets METRICS_TAG_APP and METRICS_TAG_SQUAD.
 */
class MetricsCommonTagsTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withInitializer(ctx -> applicationYml().forEach(ctx.getEnvironment().getPropertySources()::addLast))
        .withConfiguration(AutoConfigurations.of(MetricsAutoConfiguration.class,
            CompositeMeterRegistryAutoConfiguration.class, SimpleMetricsExportAutoConfiguration.class));

    @Test
    void everyMeterCarriesServiceAppAndSquad() {
        context.run(ctx -> {
            MeterRegistry registry = ctx.getBean(MeterRegistry.class);
            Gauge.builder("outbox.pending.events", () -> 0).register(registry);
            registry.counter("outbox.parked.events", "exception", "OperatorPark").increment();

            for (Meter meter : registry.getMeters()) {
                assertThat(meter.getId().getTags()).as(meter.getId().getName()).contains(
                    Tag.of("service", "svc-pay-request-to-pay"),
                    Tag.of("app", "payment-request-to-pay-service"),
                    Tag.of("squad", "payments"));
            }
            assertThat(registry.find("outbox.parked.events").tag("exception", "OperatorPark").counter()).isNotNull();
        });
    }

    @Test
    void theChartOverridesAppAndSquad() {
        context.withPropertyValues("METRICS_TAG_APP=rtp-canary", "METRICS_TAG_SQUAD=payments-canary").run(ctx -> {
            MeterRegistry registry = ctx.getBean(MeterRegistry.class);
            Gauge.builder("outbox.parked.rows", () -> 0).register(registry);

            assertThat(registry.get("outbox.parked.rows").gauge().getId().getTags())
                .contains(Tag.of("app", "rtp-canary"), Tag.of("squad", "payments-canary"));
        });
    }

    private static List<PropertySource<?>> applicationYml() {
        try {
            return new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"))
                .stream().filter(doc -> doc.getProperty("spring.config.activate.on-profile") == null).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
