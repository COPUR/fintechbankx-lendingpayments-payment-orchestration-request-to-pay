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
 * Startup TLS assertion (governance round 3, answer 2b; guardrail 4a). The context stops before
 * any bean, DataSource and Flyway included, is created unless transport encryption is verified:
 * <ul>
 *   <li>every datasource URL a pool can use ({@code spring.datasource.url}, and
 *   {@code spring.datasource.hikari.jdbc-url} and {@code spring.flyway.url} when set) carries
 *   {@code sslmode=verify-full}: the server certificate is checked against the mounted RDS CA
 *   bundle, not only encrypted ({@code require}) or checked for a CA ({@code verify-ca}). The URL
 *   is read the way PgJDBC reads it: parameter names are case-sensitive ({@code SSLMODE} is
 *   ignored and the driver falls back to {@code prefer}) and the last repeated value wins (a
 *   second {@code sslmode=disable} connects unencrypted), so a repeated {@code sslmode} or
 *   {@code sslrootcert} is refused, as are the parameters that replace or bypass certificate and
 *   host-name verification ({@code sslfactory}, {@code sslfactoryarg}, {@code sslhostnameverifier},
 *   {@code sslpasswordcallback}) or read settings from pg_service.conf ({@code service});</li>
 *   <li>when a Kafka client is configured (a {@link KafkaProperties} bean exists), the
 *   producer's effective {@code security.protocol}, after {@code spring.kafka.properties} and
 *   {@code spring.kafka.producer.properties} are applied, is {@code SASL_SSL} (MSK IAM).
 *   {@code SSL} is accepted as well for the kafka-strimzi profile (mutual TLS with a client
 *   certificate); the plaintext protocols never are. The migration Job configures no Kafka
 *   client, so only its database URLs are checked.</li>
 * </ul>
 * {@code fintechbankx.tls.enforce} is true by default, in application.yml and here; only the
 * test resources and the local profile set it false. The chart never does: its guard helpers
 * refuse FINTECHBANKX_TLS_ENFORCE, every route to the local profile and JVM options that mention
 * it. The failure names the offending setting and its sslmode or protocol, never the URL, which
 * may carry a credential.
 */
@Component
public class TlsEnforcement implements BeanFactoryPostProcessor {

    public static final String ENFORCE = "fintechbankx.tls.enforce";
    static final String REQUIRED_SSLMODE = "verify-full";
    static final Set<String> TLS_PROTOCOLS = Set.of("SASL_SSL", "SSL");
    /** Parameters that replace or bypass certificate and host-name verification, or read settings from pg_service.conf. */
    static final Set<String> VERIFICATION_BYPASS_PARAMETERS =
        Set.of("sslfactory", "sslfactoryarg", "sslhostnameverifier", "sslpasswordcallback", "service");
    /** Every datasource URL a pool can use; the first is required, the others are checked when set. */
    static final List<String> DATASOURCE_URL_SETTINGS =
        List.of("spring.datasource.url", "spring.datasource.hikari.jdbc-url", "spring.flyway.url");
    private static final Logger log = LoggerFactory.getLogger(TlsEnforcement.class);

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        Environment environment = beanFactory.getBean(Environment.class);
        if (!environment.getProperty(ENFORCE, Boolean.class, true)) {
            log.warn("{}=false: startup TLS assertion skipped (local and test configuration only)", ENFORCE);
            return;
        }
        List<String> problems = new ArrayList<>();
        Binder binder = Binder.get(environment);
        boolean first = true;
        for (String setting : DATASOURCE_URL_SETTINGS) {
            // Bound, not read: the relaxed spellings (SPRING_DATASOURCE_HIKARI_JDBCURL, jdbcUrl) reach the pool too.
            String url = binder.bind(setting, String.class).orElse(null);
            if (first || (url != null && !url.isBlank())) {
                checkDatabaseUrl(setting, url, problems);
            }
            first = false;
        }
        if (kafkaClientConfigured(beanFactory)) {
            checkKafkaProtocol(environment, problems);
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("TLS not enforced (" + ENFORCE + "=true): " + String.join("; ", problems));
        }
    }

    /** Parses the URL the way PgJDBC does: keys are case-sensitive, a repeated key keeps its last value. */
    static void checkDatabaseUrl(String setting, String url, List<String> problems) {
        if (url == null || url.isBlank()) {
            problems.add(setting + " is not set; a JDBC URL with sslmode=" + REQUIRED_SSLMODE + " is required");
            return;
        }
        int sslmodes = 0;
        int sslrootcerts = 0;
        String sslmode = null;
        List<String> misspelt = new ArrayList<>();
        List<String> bypass = new ArrayList<>();
        int query = url.indexOf('?');
        if (query >= 0) {
            for (String pair : url.substring(query + 1).split("&")) {
                int separator = pair.indexOf('=');
                String key = separator < 0 ? pair : pair.substring(0, separator);
                String value = separator < 0 ? "" : pair.substring(separator + 1);
                String lowerKey = key.toLowerCase(Locale.ROOT);
                if (VERIFICATION_BYPASS_PARAMETERS.contains(lowerKey)) {
                    bypass.add(key);
                } else if (key.equals("sslmode")) {
                    sslmodes++;
                    sslmode = value;
                } else if (key.equals("sslrootcert")) {
                    sslrootcerts++;
                } else if (lowerKey.equals("sslmode") || lowerKey.equals("sslrootcert")) {
                    misspelt.add(key);
                }
            }
        }
        if (!bypass.isEmpty()) {
            problems.add(setting + " sets " + String.join(", ", bypass)
                + ", which replaces or bypasses certificate and host-name verification; none of "
                + String.join(", ", VERIFICATION_BYPASS_PARAMETERS.stream().sorted().toList()) + " is allowed");
        }
        if (sslmodes == 0) {
            String hint = misspelt.isEmpty() ? ""
                : " (" + String.join(", ", misspelt) + " is not read: PgJDBC parameter names are case-sensitive and lower case)";
            problems.add(setting + " has no sslmode" + hint + "; sslmode=" + REQUIRED_SSLMODE + " is required");
        } else if (sslmodes > 1) {
            problems.add(setting + " sets sslmode " + sslmodes + " times (PgJDBC keeps the last, " + sslmode
                + "); exactly one sslmode=" + REQUIRED_SSLMODE + " is required");
        } else if (!REQUIRED_SSLMODE.equals(sslmode)) {
            problems.add(setting + " has sslmode=" + sslmode + "; sslmode=" + REQUIRED_SSLMODE + " is required");
        }
        if (sslrootcerts > 1) {
            problems.add(setting + " sets sslrootcert " + sslrootcerts + " times (PgJDBC keeps the last); at most one is allowed");
        } else if (!misspelt.isEmpty() && sslmodes > 0) {
            problems.add(setting + " spells " + String.join(", ", misspelt)
                + " in a case PgJDBC does not read; parameter names are case-sensitive and lower case");
        }
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
            problems.add("spring.kafka.security.protocol (effective producer value, after spring.kafka.properties and"
                + " spring.kafka.producer.properties) is " + protocol + "; SASL_SSL is required (SSL only for Strimzi mutual TLS)");
        }
    }
}
