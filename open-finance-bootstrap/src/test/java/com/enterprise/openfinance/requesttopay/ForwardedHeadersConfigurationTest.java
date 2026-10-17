package com.enterprise.openfinance.requesttopay;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The DPoP htu must be the public URL the TPP called. The ingress gateway sets
 * X-Forwarded-Proto/Host/Port (overwriting client values), so the service must
 * apply them with Spring's framework strategy (platform contract, "Forwarded headers (htu)").
 */
class ForwardedHeadersConfigurationTest {

    @Test
    void forwardedHeadersAreAppliedByTheFrameworkFilter() throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);

        assertThat(Binder.get(environment).bind("server.forward-headers-strategy", String.class).get())
                .isEqualTo("framework");
    }
}
