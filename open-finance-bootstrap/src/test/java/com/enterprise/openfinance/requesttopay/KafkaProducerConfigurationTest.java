package com.enterprise.openfinance.requesttopay;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The shipped producer settings must be accepted by the Kafka client: it refuses to start when
 * delivery.timeout.ms is below linger.ms + request.timeout.ms, and the outbox relay must wait
 * longer than the producer's own delivery timeout so it sees the real outcome of a send.
 */
class KafkaProducerConfigurationTest {

    @Test
    void producerSettingsFromApplicationYmlAreAcceptedByTheKafkaClient() throws IOException {
        Map<String, Object> producerProperties = kafkaProperties().buildProducerProperties(null);

        assertThatCode(() -> {
            try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties)) {
                producer.close(Duration.ZERO);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void deliveryTimeoutCoversLingerPlusRequestTimeoutAndRelayWaitsLonger() throws IOException {
        Map<String, Object> producer = kafkaProperties().buildProducerProperties(null);
        long deliveryTimeout = Long.parseLong(producer.get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG).toString());
        long linger = Long.parseLong(producer.get(ProducerConfig.LINGER_MS_CONFIG).toString());
        long requestTimeout = Long.parseLong(producer.get(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG).toString());
        Duration relaySendTimeout = binder().bind("requesttopay.outbox.relay.send-timeout", Duration.class).get();

        assertThat(requestTimeout).isEqualTo(20_000L);
        assertThat(deliveryTimeout).isGreaterThanOrEqualTo(linger + requestTimeout);
        assertThat(relaySendTimeout).isEqualTo(Duration.ofSeconds(35));
        assertThat(relaySendTimeout.toMillis()).isGreaterThan(deliveryTimeout);
    }

    private static KafkaProperties kafkaProperties() throws IOException {
        return binder().bind("spring.kafka", KafkaProperties.class).get();
    }

    private static Binder binder() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);
        return Binder.get(environment);
    }
}
