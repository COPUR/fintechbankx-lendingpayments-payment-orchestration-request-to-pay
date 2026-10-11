package com.enterprise.openfinance.requesttopay;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A relay send can block for up to the relay send-timeout. The shutdown phase must outlast it,
 * and the pod's terminationGracePeriodSeconds must outlast the preStop sleep plus that phase,
 * otherwise Kubernetes kills the JVM in the middle of an outbox batch.
 */
class GracefulShutdownConfigurationTest {

    private static final Path DEPLOYMENT_TEMPLATE =
            Path.of("..", "deploy", "helm", "payment-request-to-pay-service", "templates", "deployment.yaml");

    @Test
    void shutdownPhaseOutlastsTheLongestRelaySend() throws IOException {
        Binder binder = binder();
        Duration shutdownPhase = binder.bind("spring.lifecycle.timeout-per-shutdown-phase", Duration.class).get();
        Duration relaySendTimeout = binder.bind("requesttopay.outbox.relay.send-timeout", Duration.class).get();

        assertThat(shutdownPhase).isEqualTo(Duration.ofSeconds(40));
        assertThat(shutdownPhase).isGreaterThan(relaySendTimeout);
    }

    @Test
    void podGracePeriodCoversPreStopSleepPlusShutdownPhase() throws IOException {
        String deployment = Files.readString(DEPLOYMENT_TEMPLATE);
        long gracePeriod = firstNumber(deployment, "terminationGracePeriodSeconds:\\s*(\\d+)");
        long preStopSleep = firstNumber(deployment, "sleep (\\d+)");
        Duration shutdownPhase = binder().bind("spring.lifecycle.timeout-per-shutdown-phase", Duration.class).get();

        assertThat(gracePeriod).isGreaterThan(preStopSleep + shutdownPhase.toSeconds());
    }

    private static long firstNumber(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        assertThat(matcher.find()).as("pattern %s in deployment.yaml", regex).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private static Binder binder() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);
        return Binder.get(environment);
    }
}
