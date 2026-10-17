package com.enterprise.openfinance.requesttopay.infrastructure.outbox;

import com.enterprise.openfinance.requesttopay.domain.command.CreatePayRequestCommand;
import com.enterprise.openfinance.requesttopay.domain.event.PayRequestDomainEvent;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.enterprise.openfinance.requesttopay.domain.model.valueobject.DecisionBy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provider contract: every event PayRequestEventEnvelopeFactory can produce is a
 * message of the one aggregate channel evt.pay.rtp.v1 in
 * api/asyncapi/svc-pay-request-to-pay.yaml (ADR-019, one topic per aggregate),
 * with the same eventType const in the payload and in the eventType record
 * header, the common envelope and headers, and data fields the schema declares.
 */
class AsyncApiProviderContractTest {

    private static final Instant NOW = Instant.parse("2026-02-10T10:00:01Z");
    private static Map<String, Object> spec;

    @BeforeAll
    static void load() throws Exception {
        Path file = Path.of("..", "api", "asyncapi", "svc-pay-request-to-pay.yaml");
        try (Reader reader = Files.newBufferedReader(file)) {
            spec = new Yaml().load(reader);
        }
    }

    @Test
    void everyPublishedEventIsAMessageOfTheAggregateChannel() {
        Map<String, Object> rtp = map(map(spec.get("channels")).get("rtp"));
        assertThat(rtp.get("address")).isEqualTo(PayRequestEventEnvelopeFactory.TOPIC);
        assertThat(map(map(rtp.get("bindings")).get("kafka")).get("topic")).isEqualTo(PayRequestEventEnvelopeFactory.TOPIC);
        assertThat(map(spec.get("channels"))).as("one topic per aggregate; no dead-letter topic, nothing is consumed")
            .containsOnlyKeys("rtp");
        Set<String> seen = new HashSet<>();

        for (PayRequestDomainEvent event : everyKindOfEvent()) {
            PayRequestEventEnvelopeFactory.PublicEvent mapped = PayRequestEventEnvelopeFactory.map(event);
            String name = mapped.eventType().replace("Payments.PayRequest.", "").replace(".v1", "");
            seen.add(name);

            assertThat(map(rtp.get("messages"))).as(name + " on " + PayRequestEventEnvelopeFactory.TOPIC)
                .containsKey("PayRequest" + name);
            Map<String, Object> message = map(map(map(spec.get("components")).get("messages")).get("PayRequest" + name));
            assertThat(message.get("title")).isEqualTo(mapped.eventType());
            assertThat(eventTypeConst(message.get("payload"))).as(name + " payload eventType").isEqualTo(mapped.eventType());
            assertThat(eventTypeConst(message.get("headers"))).as(name + " eventType header").isEqualTo(mapped.eventType());
            assertThat(String.valueOf(message.get("payload"))).contains("#/components/schemas/EventEnvelope");
            assertThat(String.valueOf(message.get("headers"))).contains("#/components/schemas/EventHeaders");

            Map<String, Object> schema = map(map(map(spec.get("components")).get("schemas")).get("PayRequest" + name + "Data"));
            assertThat(map(schema.get("properties")).keySet()).as(name + " declared data fields").containsAll(mapped.data().keySet());
            Object required = schema.get("required");
            if (required != null) {
                assertThat(mapped.data().keySet()).as(name + " required fields present")
                    .containsAll(((List<?>) required).stream().map(String::valueOf).toList());
            }
        }
        assertThat(seen).containsExactlyInAnyOrder("Created", "Accepted", "Rejected");
        assertThat(map(rtp.get("messages"))).hasSize(seen.size());
    }

    /**
     * ADR-019 section 1: a dead-letter topic belongs to the consumer that gives up on a message.
     * This service consumes nothing (every operation sends), so its contract declares no dead-letter
     * channel, message or header schema.
     */
    @Test
    void nothingIsConsumedSoNoDeadLetterChannelMessageOrSchemaIsDeclared() {
        Map<String, Object> components = map(spec.get("components"));
        for (Object operation : map(spec.get("operations")).values()) {
            assertThat(map(operation).get("action")).isEqualTo("send");
        }
        assertThat(map(spec.get("channels")).keySet()).noneMatch(key -> key.toLowerCase().contains("dlq")
            || key.toLowerCase().contains("deadletter") || key.toLowerCase().contains("dead-letter"));
        assertThat(map(components.get("messages")).keySet()).as("no orphan DeadLetter message")
            .noneMatch(key -> key.contains("DeadLetter"));
        assertThat(map(components.get("schemas")).keySet()).as("no unreferenced DeadLetterHeaders schema")
            .noneMatch(key -> key.contains("DeadLetter"));
    }

    /**
     * The decision events say what the requesting TPP reported; this service verifies neither the
     * debtor's authorisation nor the payment, and the record key is a pay request id, not a consent
     * of consent-authorization (wording of the provider copy, kept over the catalog's summaries).
     */
    @Test
    void decisionSummariesAndKeyDescriptionsKeepTheProvidersHonestWording() {
        Map<String, Object> messages = map(map(spec.get("components")).get("messages"));

        assertThat(String.valueOf(map(messages.get("PayRequestAccepted")).get("summary")))
            .contains("requesting TPP reported acceptance")
            .contains("not verified by this service");
        assertThat(String.valueOf(map(messages.get("PayRequestRejected")).get("summary")))
            .contains("requesting TPP reported that the debtor declined");
        for (String name : List.of("PayRequestCreated", "PayRequestAccepted", "PayRequestRejected")) {
            Map<String, Object> key = map(map(map(map(messages.get(name)).get("bindings")).get("kafka")).get("key"));
            assertThat(String.valueOf(key.get("description"))).as(name + " key")
                .contains("the pay request id")
                .contains("Data.ConsentId on the TPP API")
                .contains("not a consent-authorization consent")
                .doesNotContain("consentId of the pay request");
        }
    }

    @Test
    void theCommonEnvelopeAndHeadersAreReferenced() {
        Map<String, Object> schemas = map(map(spec.get("components")).get("schemas"));
        assertThat(map(schemas.get("EventEnvelope")).get("$ref")).isEqualTo("./common/event-envelope.yaml#/EventEnvelope");
        assertThat(map(schemas.get("EventHeaders")).get("$ref")).isEqualTo("./common/event-envelope.yaml#/EventHeaders");
        assertThat(Path.of("..", "api", "asyncapi", "common", "event-envelope.yaml")).exists();
    }

    private static List<PayRequestDomainEvent> everyKindOfEvent() {
        List<PayRequestDomainEvent> events = new ArrayList<>();
        PayRequest created = PayRequest.create("CONS-CONTRACT-1", command(), NOW);
        events.addAll(created.domainEvents());
        events.addAll(created.consume("PAY-1", new DecisionBy("TPP-001", "ok"), NOW.plusSeconds(60)).domainEvents());
        events.addAll(PayRequest.create("CONS-CONTRACT-2", command(), NOW)
            .reject(new DecisionBy("TPP-001", "declined"), NOW.plusSeconds(60)).domainEvents());
        return events;
    }

    private static CreatePayRequestCommand command() {
        return new CreatePayRequestCommand("TPP-001", "PSU-001", "Utilities Co", new BigDecimal("500"), "AED",
            NOW.minusSeconds(1), "ix-1");
    }

    /** The eventType const of the second allOf part (after the common envelope or headers $ref). */
    private static Object eventTypeConst(Object schema) {
        List<?> allOf = (List<?>) map(schema).get("allOf");
        assertThat(allOf).hasSize(2);
        return map(map(map(allOf.get(1)).get("properties")).get("eventType")).get("const");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }
}
