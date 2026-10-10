package com.enterprise.openfinance.requesttopay;

import org.apache.kafka.clients.CommonClientConfigs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Startup TLS assertion (governance round 3, answer 2b). The context stops before any
 * bean, DataSource and Flyway included, is created unless transport encryption is
 * verified:
 * <ul>
 *   <li>{@code spring.datasource.url} (and {@code spring.flyway.url} when set) carries
 *   {@code sslmode=verify-full}: the server certificate is checked against the mounted RDS
 *   CA bundle, not only encrypted ({@code require}) or checked for a CA ({@code verify-ca});</li>
 *   <li>when a Kafka client is configured (a {@link KafkaProperties} bean exists), the
 *   producer's effective {@code security.protocol} is {@code SASL_SSL} (MSK IAM).
 *   {@code SSL} is accepted as well for the kafka-strimzi profile (mutual TLS with a client
 *   certificate); the plaintext protocols never are. The migration Job configures no Kafka
 *   client, so only its database URL is checked.</li>
 * </ul>
 * {@code fintechbankx.tls.enforce} is true by default, in application.yml and here; only the
 * test resources and the local profile set it false. The chart never does: its
 * rtp.guardEnvKeys helper refuses FINTECHBANKX_TLS_ENFORCE. The failure names the offending
 * setting and its sslmode or protocol, never the URL, which may carry a credential.
 */
@Component
public class TlsEnforcement implements BeanFactoryPostProcessor {

    public static final String ENFORCE = "fintechbankx.tls.enforce";
    static final String REQUIRED_SSLMODE = "verify-full";
    static final Set<String> TLS_PROTOCOLS = Set.of("SASL_SSL", "SSL");
    private static final Logger log = LoggerFactory.getLogger(TlsEnforcement.class);

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        Environment environment = beanFactory.getBean(Environment.class);
        if (!environment.getProperty(ENFORCE, Boolean.class, true)) {
            log.warn("{}=false: startup TLS assertion skipped (local and test configuration only)", ENFORCE);
            return;
        }
        List<String> problems = new ArrayList<>();
        checkDatabaseUrl("spring.datasource.url", environment.getProperty("spring.datasource.url"), problems);
        String flywayUrl = environment.getProperty("spring.flyway.url");
        if (flywayUrl != null && !flywayUrl.isBlank()) {
            checkDatabaseUrl("spring.flyway.url", flywayUrl, problems);
        }
        if (kafkaClientConfigured(beanFactory)) {
            checkKafkaProtocol(environment, problems);
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("TLS not enforced (" + ENFORCE + "=true): " + String.join("; ", problems));
        }
    }

    static void checkDatabaseUrl(String setting, String url, List<String> problems) {
        if (url == null || url.isBlank()) {
            problems.add(setting + " is not set; a JDBC URL with sslmode=" + REQUIRED_SSLMODE + " is required");
            return;
        }
        String sslmode = queryParameter(url, "sslmode");
        if (sslmode == null) {
            problems.add(setting + " has no sslmode; sslmode=" + REQUIRED_SSLMODE + " is required");
        } else if (!REQUIRED_SSLMODE.equals(sslmode)) {
            problems.add(setting + " has sslmode=" + sslmode + "; sslmode=" + REQUIRED_SSLMODE + " is required");
        }
    }

    /** The value of one query parameter of a JDBC URL, or null when it is absent. */
    static String queryParameter(String url, String name) {
        int query = url.indexOf('?');
        if (query < 0) {
            return null;
        }
        for (String pair : url.substring(query + 1).split("&")) {
            int separator = pair.indexOf('=');
            String key = separator < 0 ? pair : pair.substring(0, separator);
            if (key.equals(name)) {
                return separator < 0 ? "" : pair.substring(separator + 1);
            }
        }
        return null;
    }

    private static boolean kafkaClientConfigured(ConfigurableListableBeanFactory beanFactory) {
        return beanFactory.getBeanNamesForType(KafkaProperties.class, true, false).length > 0;
    }

    /** The producer's effective security.protocol: spring.kafka.properties and the producer's own settings override the common one. */
    static void checkKafkaProtocol(Environment environment, List<String> problems) {
        KafkaProperties kafka = Binder.get(environment).bind("spring.kafka", KafkaProperties.class).orElseGet(KafkaProperties::new);
        Object protocol = kafka.buildProducerProperties(null).get(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG);
        if (protocol == null) {
            problems.add("spring.kafka.security.protocol is not set (the Kafka client defaults to PLAINTEXT); SASL_SSL is required");
        } else if (!TLS_PROTOCOLS.contains(protocol.toString().toUpperCase(Locale.ROOT))) {
            problems.add("spring.kafka.security.protocol is " + protocol + "; SASL_SSL is required (SSL only for Strimzi mutual TLS)");
        }
    }
}
