package com.enterprise.openfinance.requesttopay;

import com.enterprise.openfinance.requesttopay.domain.command.CreatePayRequestCommand;
import com.enterprise.openfinance.requesttopay.domain.exception.PayRequestFinalizedException;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestResult;
import com.enterprise.openfinance.requesttopay.domain.port.in.PayRequestUseCase;
import com.enterprise.openfinance.requesttopay.infrastructure.outbox.OutboxEventJpaEntity;
import com.enterprise.openfinance.requesttopay.infrastructure.outbox.OutboxRelay;
import com.enterprise.openfinance.requesttopay.infrastructure.outbox.PostgresSessionRelayLock;
import com.enterprise.openfinance.requesttopay.infrastructure.outbox.RelayLock;
import com.enterprise.openfinance.requesttopay.infrastructure.outbox.SpringDataOutboxRepository;
import com.enterprise.openfinance.requesttopay.infrastructure.security.DPoPValidationService;
import com.enterprise.openfinance.requesttopay.infrastructure.security.JdbcDPoPNonceRepository;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.util.Date;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the whole service against PostgreSQL: Flyway builds
 * sc_pay_request_to_pay, Hibernate validates the entities against it, and a
 * pay request goes through its lifecycle over HTTP with its events landing in
 * the outbox and then on (a mocked) Kafka.
 */
@SpringBootTest(properties = "requesttopay.outbox.relay.enabled=false")
@AutoConfigureMockMvc
class RequestToPayServiceIT {

    private static final String SCHEMA = "sc_pay_request_to_pay";
    private static final String BODY = """
            {"Data": {"PsuId": "PSU-001", "CreditorName": "Utilities Co",
                      "InstructedAmount": {"Amount": "%s", "Currency": "AED"}}}
            """;

    @BeforeAll
    static void requireDatabase() {
        PostgresTestDatabase.requireDatabase();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PayRequestUseCase useCase;
    @Autowired SpringDataOutboxRepository outbox;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcDPoPNonceRepository dpopJti;
    @Autowired DataSource dataSource;
    @MockBean JwtDecoder jwtDecoder;

    private static final ECKey TPP_KEY = newKey();

    private static ECKey newKey() {
        try {
            return new ECKeyGenerator(Curve.P_256).generate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @BeforeEach
    void tokensAreBoundToTheTppKey() {
        when(jwtDecoder.decode(any())).thenAnswer(call -> boundToken(call.getArgument(0)));
    }

    /** As the schema owner: the runtime role the service runs as may not delete pay requests. */
    @BeforeEach
    void cleanTables() {
        JdbcTemplate owner = PostgresTestDatabase.owner();
        owner.update("delete from " + SCHEMA + ".outbox_event");
        owner.update("delete from " + SCHEMA + ".pay_request_idempotency");
        owner.update("delete from " + SCHEMA + ".pay_request");
        owner.update("delete from " + SCHEMA + ".dpop_proof_jti");
    }

    /**
     * Platform review item 4: the service connects as a runtime role with
     * only the DML its code issues (V5); Flyway ran as the schema owner.
     */
    @Test
    void theRuntimeRoleHasOnlyTheDmlTheServiceIssues() {
        assertThat(jdbc.queryForObject("select current_user", String.class))
                .isEqualTo(PostgresTestDatabase.RUNTIME_ROLE);
        assertThat(PostgresTestDatabase.owner().queryForObject(
                "select tableowner from pg_tables where schemaname = ? and tablename = 'pay_request'", String.class, SCHEMA))
                .isEqualTo(PostgresTestDatabase.ownerUser());
        for (String forbidden : List.of(
                "create table " + SCHEMA + ".rogue (id int)",
                "alter table " + SCHEMA + ".pay_request add column rogue int",
                "drop table " + SCHEMA + ".dpop_proof_jti",
                "truncate " + SCHEMA + ".outbox_event",
                "delete from " + SCHEMA + ".pay_request",
                "update " + SCHEMA + ".pay_request_idempotency set expires_at = now()",
                "update " + SCHEMA + ".dpop_proof_jti set expires_at = now()",
                "select count(*) from " + SCHEMA + ".flyway_schema_history")) {
            // Postgres says "permission denied" for missing grants and "must be owner" for DDL.
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.execute(forbidden))
                    .as(forbidden)
                    .satisfies(e -> assertThat(org.springframework.core.NestedExceptionUtils.getMostSpecificCause(e).getMessage())
                            .containsAnyOf("permission denied", "must be owner"));
        }
    }

    @Test
    void flywayCreatesOnlyTheTablesThisServiceOwns() {
        List<String> tables = jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'sc_pay_request_to_pay' and table_name <> 'flyway_schema_history'
                order by table_name
                """, String.class);

        assertThat(tables).containsExactly("dpop_proof_jti", "outbox_event", "pay_request", "pay_request_idempotency");
    }

    @Test
    void lifecycleOverHttpPersistsStateAndWritesEveryEventToTheOutbox() throws Exception {
        String consentId = create("idem-life", "500.00", "ix-create");

        mvc.perform(asTpp(get("/open-finance/v1/payment-consents/{id}", consentId)).header("X-FAPI-Interaction-ID", "ix-get"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Data.Status").value("AwaitingAuthorisation"));
        mvc.perform(asTpp(post("/open-finance/v1/payment-consents/{id}/accept", consentId))
                        .header("X-FAPI-Interaction-ID", "ix-accept")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentId\": \"PAY-777\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.Data.Status").value("Consumed"))
                .andExpect(jsonPath("$.Data.PaymentId").value("PAY-777"));
        mvc.perform(asTpp(post("/open-finance/v1/payment-consents/{id}/reject", consentId))
                        .header("X-FAPI-Interaction-ID", "ix-reject")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_FINALIZED"));

        Map<String, Object> row = jdbc.queryForMap(
                "select status, payment_id, version, amount from " + SCHEMA + ".pay_request where consent_id = ?", consentId);
        assertThat(row.get("status")).isEqualTo("CONSUMED");
        assertThat(row.get("payment_id")).isEqualTo("PAY-777");
        assertThat(((Number) row.get("version")).longValue()).isEqualTo(1);
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("500.00");

        List<Map<String, Object>> events = jdbc.queryForList("""
                select topic, event_type, aggregate_version, correlation_id, payload::text as payload, status
                from sc_pay_request_to_pay.outbox_event order by created_seq""");
        assertThat(events).extracting(e -> e.get("topic"))
                .containsExactly("evt.pay.rtp.v1", "evt.pay.rtp.v1");
        assertThat(events).extracting(e -> e.get("event_type"))
                .containsExactly("Payments.PayRequest.Created.v1", "Payments.PayRequest.Accepted.v1");
        assertThat(events).extracting(e -> ((Number) e.get("aggregate_version")).longValue()).containsExactly(0L, 1L);
        assertThat(events).extracting(e -> e.get("correlation_id")).containsExactly("ix-create", "ix-accept");
        JsonNode accepted = json.readTree((String) events.get(1).get("payload"));
        assertThat(accepted.get("eventType").asText()).isEqualTo("Payments.PayRequest.Accepted.v1");
        assertThat(accepted.get("producer").asText()).isEqualTo("svc-pay-request-to-pay");
        assertThat(accepted.get("data").get("amount").asText()).isEqualTo("500.00");
        assertThat(accepted.get("data").get("paymentId").asText()).isEqualTo("PAY-777");
    }

    @Test
    void aRepeatedDecisionReturnsTheCurrentStateAndPublishesNothingNew() throws Exception {
        String accepted = create("idem-repeat-a", "15.00", "ix-repeat-a");
        for (int call = 0; call < 2; call++) {
            mvc.perform(asTpp(post("/open-finance/v1/payment-consents/{id}/accept", accepted))
                            .header("X-FAPI-Interaction-ID", "ix-repeat-accept-" + call)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"paymentId\": \"PAY-R1\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.Data.Status").value("Consumed"))
                    .andExpect(jsonPath("$.Data.PaymentId").value("PAY-R1"));
        }
        mvc.perform(asTpp(post("/open-finance/v1/payment-consents/{id}/accept", accepted))
                        .header("X-FAPI-Interaction-ID", "ix-repeat-other")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentId\": \"PAY-R2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_FINALIZED"));

        String rejected = create("idem-repeat-r", "16.00", "ix-repeat-r");
        for (int call = 0; call < 2; call++) {
            mvc.perform(asTpp(post("/open-finance/v1/payment-consents/{id}/reject", rejected))
                            .header("X-FAPI-Interaction-ID", "ix-repeat-reject-" + call)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"debtor declined\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.Data.Status").value("Rejected"));
        }

        assertThat(jdbc.queryForObject("select version from " + SCHEMA + ".pay_request where consent_id = ?",
                Long.class, accepted)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select version from " + SCHEMA + ".pay_request where consent_id = ?",
                Long.class, rejected)).isEqualTo(1L);
        // created + accepted, created + rejected: the repeats published nothing
        assertThat(count("outbox_event")).isEqualTo(4);
        JsonNode rejectedEvent = json.readTree(jdbc.queryForObject("select payload::text from " + SCHEMA
                + ".outbox_event where aggregate_id = ? and aggregate_version = 1", String.class, rejected));
        assertThat(rejectedEvent.get("data").get("actorClientId").asText()).isEqualTo("TPP-001");
        assertThat(rejectedEvent.get("data").get("reason").asText()).isEqualTo("debtor declined");
    }

    @Test
    void anotherTppCannotReadOrDecideTheRequest() throws Exception {
        String consentId = create("idem-own", "10.00", "ix-own");

        mvc.perform(get("/open-finance/v1/payment-consents/{id}", consentId)
                        .with(dpop("TPP-OTHER"))
                        .header("X-FAPI-Interaction-ID", "ix"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/open-finance/v1/payment-consents/{id}/reject", consentId)
                        .with(dpop("TPP-OTHER"))
                        .header("X-FAPI-Interaction-ID", "ix")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("select status from " + SCHEMA + ".pay_request where consent_id = ?",
                String.class, consentId)).isEqualTo("AWAITING_AUTHORISATION");
    }

    @Test
    void anUnknownPayRequestAndAnotherTppsGetTheSame404Body() throws Exception {
        String othersConsent = create("idem-probe", "10.00", "ix-probe");
        String unknownConsent = "CONS-RTP2-does-not-exist";

        for (String action : List.of("", "/accept", "/reject")) {
            JsonNode unknown = refusedBody(action, unknownConsent);
            JsonNode notOwned = refusedBody(action, othersConsent);

            ((com.fasterxml.jackson.databind.node.ObjectNode) unknown).remove("timestamp");
            ((com.fasterxml.jackson.databind.node.ObjectNode) notOwned).remove("timestamp");
            assertThat(unknown).as("same code, message and fields for '%s'; only the timestamp differs", action)
                    .isEqualTo(notOwned);
            assertThat(unknown.get("code").asText()).isEqualTo("NOT_FOUND");
            assertThat(unknown.get("message").asText()).isEqualTo("Pay request not found");
        }
        assertThat(jdbc.queryForObject("select status from " + SCHEMA + ".pay_request where consent_id = ?",
                String.class, othersConsent)).isEqualTo("AWAITING_AUTHORISATION");
    }

    /** The response of TPP-OTHER reading ("") or deciding ("/accept", "/reject") {@code consentId}; must be 404. */
    private JsonNode refusedBody(String action, String consentId) throws Exception {
        String path = "/open-finance/v1/payment-consents/" + consentId + action;
        MockHttpServletRequestBuilder request = action.isEmpty() ? get(path)
                : post(path).contentType(MediaType.APPLICATION_JSON).content("{\"paymentId\": \"PAY-PROBE\"}");
        String body = mvc.perform(request.with(dpop("TPP-OTHER")).header("X-FAPI-Interaction-ID", "ix-probe"))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        return new ObjectMapper().readTree(body);
    }

    @Test
    void retryWithSameKeyReturnsTheFirstRequestAndDifferentPayloadIsAConflict() throws Exception {
        String first = create("idem-retry", "250.00", "ix-1");

        mvc.perform(createRequest("idem-retry", "250.0", "ix-2"))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.Data.ConsentId").value(first));
        mvc.perform(createRequest("idem-retry", "999.00", "ix-3"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(count("pay_request")).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(1);
    }

    @Test
    void anExpiredKeyCanBeReusedForANewRequest() throws Exception {
        String first = create("idem-expired", "250.00", "ix-exp-1");
        // As the owner: the service only inserts, reads and deletes idempotency keys.
        PostgresTestDatabase.owner().update("update " + SCHEMA + ".pay_request_idempotency set expires_at = now() - interval '1 second' "
                + "where tpp_id = 'TPP-001' and idempotency_key = 'idem-expired'");

        String response = mvc.perform(createRequest("idem-expired", "999.00", "ix-exp-2"))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Idempotent-Replay", "false"))
                .andReturn().getResponse().getContentAsString();
        String second = json.readTree(response).get("Data").get("ConsentId").asText();

        assertThat(second).isNotEqualTo(first);
        assertThat(count("pay_request")).isEqualTo(2);
        assertThat(count("pay_request_idempotency")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select consent_id from " + SCHEMA + ".pay_request_idempotency "
                + "where tpp_id = 'TPP-001' and idempotency_key = 'idem-expired'", String.class)).isEqualTo(second);
        assertThat(count("outbox_event")).isEqualTo(2);
    }

    @Test
    void concurrentCreatesWithTheSameKeyProduceOnePayRequest() throws Exception {
        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<PayRequestResult>> results = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            String interaction = "ix-race-" + i;
            Callable<PayRequestResult> call = () -> {
                start.await();
                return useCase.createPayRequest(new CreatePayRequestCommand("TPP-001", "PSU-001", "Utilities Co",
                        new BigDecimal("75.00"), "AED", Instant.now(), interaction, "idem-race"));
            };
            results.add(pool.submit(call));
        }
        start.countDown();

        Set<String> consentIds = new HashSet<>();
        int replays = 0;
        for (Future<PayRequestResult> result : results) {
            PayRequestResult r = result.get();
            consentIds.add(r.request().consentId());
            replays += r.idempotentReplay() ? 1 : 0;
        }
        pool.shutdown();

        assertThat(consentIds).hasSize(1);
        assertThat(replays).isEqualTo(callers - 1);
        assertThat(count("pay_request")).isEqualTo(1);
        assertThat(count("pay_request_idempotency")).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(1);
    }

    @Test
    void concurrentAcceptAndRejectLetExactlyOneDecisionWin() throws Exception {
        String consentId = useCase.createPayRequest(new CreatePayRequestCommand("TPP-001", "PSU-001", "Utilities Co",
                new BigDecimal("40.00"), "AED", Instant.now(), "ix-c")).request().consentId();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Object> accept = pool.submit(() -> decide(start, () -> useCase.acceptPayRequest(consentId, "TPP-001", "PAY-1", null, "ix-a")));
        Future<Object> reject = pool.submit(() -> decide(start, () -> useCase.rejectPayRequest(consentId, "TPP-001", null, "ix-r")));
        start.countDown();

        List<Object> outcomes = List.of(accept.get(), reject.get());
        pool.shutdown();

        assertThat(outcomes).filteredOn(o -> o instanceof PayRequestResult).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o instanceof PayRequestFinalizedException).hasSize(1);
        assertThat(jdbc.queryForObject("select version from " + SCHEMA + ".pay_request where consent_id = ?",
                Long.class, consentId)).isEqualTo(1L);
        assertThat(count("outbox_event")).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void relayPublishesPendingEventsKeyedByConsentIdAndMarksThemPublished() throws Exception {
        String consentId = create("idem-relay", "60.00", "ix-relay");
        KafkaTemplate<String, String> kafka = Mockito.mock(KafkaTemplate.class);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        OutboxRelay relay = relay(kafka, Duration.ofSeconds(5));

        assertThat(relay.relayOnce()).isEqualTo(1);
        assertThat(relay.relayOnce()).isZero();

        org.mockito.ArgumentCaptor<ProducerRecord<String, String>> sent = org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        Mockito.verify(kafka).send(sent.capture());
        // One topic per aggregate (ADR-019): evt.pay.rtp.v1, key = aggregateId, required headers = envelope.
        assertThat(sent.getValue().topic()).isEqualTo("evt.pay.rtp.v1");
        assertThat(sent.getValue().key()).isEqualTo(consentId);
        JsonNode envelope = json.readTree(sent.getValue().value());
        assertThat(envelope.get("aggregateId").asText()).isEqualTo(consentId);
        assertThat(recordHeader(sent.getValue(), "eventType")).isEqualTo(envelope.get("eventType").asText())
                .isEqualTo("Payments.PayRequest.Created.v1");
        assertThat(recordHeader(sent.getValue(), "eventId")).isEqualTo(envelope.get("eventId").asText());
        assertThat(recordHeader(sent.getValue(), "correlationId")).isEqualTo(envelope.get("correlationId").asText())
                .isEqualTo("ix-relay");
        assertThat(recordHeader(sent.getValue(), "x-fapi-interaction-id")).isEqualTo("ix-relay");
        assertThat(outbox.countByStatus(OutboxEventJpaEntity.Status.PUBLISHED)).isEqualTo(1);
        assertThat(outbox.countByStatus(OutboxEventJpaEntity.Status.PENDING)).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aParkedEventHoldsBackItsAggregatesLaterEventsWhileOtherAggregatesFlow() throws Exception {
        String parkedAggregate = create("idem-park-a", "10.00", "ix-park-a");
        mvc.perform(asTpp(post("/open-finance/v1/payment-consents/{id}/accept", parkedAggregate))
                        .header("X-FAPI-Interaction-ID", "ix-park-accept")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentId\": \"PAY-P1\"}"))
                .andExpect(status().isCreated());
        String otherAggregate = create("idem-park-b", "20.00", "ix-park-b");
        // the runbook's operator park: status, parked_at and a recorded reason; park_counted stays false
        jdbc.update("update " + SCHEMA + ".outbox_event set status = 'PARKED', parked_at = now(), "
                + "park_reason = 'operator: INC-1 broker ACL' where aggregate_id = ? and aggregate_version = 0", parkedAggregate);
        KafkaTemplate<String, String> kafka = Mockito.mock(KafkaTemplate.class);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        io.micrometer.core.instrument.simple.SimpleMeterRegistry meters = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        OutboxRelay countingRelay = relay(kafka, Duration.ofSeconds(5), meters);

        assertThat(countingRelay.relayOnce()).isEqualTo(1);
        assertThat(countingRelay.relayOnce()).isZero();
        assertThat(meters.get("outbox.parked.events").tag("exception", "OperatorPark").counter().count())
                .as("the operator park is counted once, not on every run").isEqualTo(1.0);
        assertThat(jdbc.queryForObject("select park_counted from " + SCHEMA + ".outbox_event where status = 'PARKED' "
                + "and aggregate_id = ?", Boolean.class, parkedAggregate)).isTrue();

        org.mockito.ArgumentCaptor<ProducerRecord<String, String>> sent = org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        Mockito.verify(kafka).send(sent.capture());
        assertThat(sent.getValue().key()).isEqualTo(otherAggregate);
        assertThat(jdbc.queryForObject("select status from " + SCHEMA + ".outbox_event where aggregate_id = ? "
                + "and aggregate_version = 1", String.class, parkedAggregate)).isEqualTo("PENDING");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aSendThatNeverCompletesHoldsNoTransactionAndMarksNothing() throws Exception {
        String consentId = create("idem-stuck", "30.00", "ix-stuck");
        KafkaTemplate<String, String> kafka = Mockito.mock(KafkaTemplate.class);
        List<Boolean> transactionOpenDuringSend = new ArrayList<>();
        List<Long> openTransactionsOfThisDatabase = new ArrayList<>();
        when(kafka.send(any(ProducerRecord.class))).thenAnswer(call -> {
            transactionOpenDuringSend.add(TransactionSynchronizationManager.isActualTransactionActive());
            openTransactionsOfThisDatabase.add(jdbc.queryForObject("""
                    select count(*) from pg_stat_activity
                    where datname = current_database() and state = 'idle in transaction'""", Long.class));
            return new CompletableFuture<>();
        });

        assertThat(relay(kafka, Duration.ofMillis(200)).relayOnce()).isZero();

        assertThat(transactionOpenDuringSend).containsExactly(false);
        assertThat(openTransactionsOfThisDatabase).containsExactly(0L);
        Map<String, Object> row = jdbc.queryForMap("select status, attempts, last_error from "
                + SCHEMA + ".outbox_event where aggregate_id = ?", consentId);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(((Number) row.get("attempts")).intValue()).isZero();
        assertThat(row.get("last_error")).isNull();
    }

    @Test
    void onlyOneRelayHoldsTheSessionLockAndItIsReleasedAfterTheRun() {
        PostgresSessionRelayLock first = new PostgresSessionRelayLock(dataSource, OutboxRelay.RELAY_LOCK_KEY);
        PostgresSessionRelayLock second = new PostgresSessionRelayLock(dataSource, OutboxRelay.RELAY_LOCK_KEY);

        try (RelayLock.Held held = first.tryAcquire().orElseThrow()) {
            assertThat(second.tryAcquire()).isEmpty();
        }
        Optional<RelayLock.Held> again = second.tryAcquire();
        assertThat(again).isPresent();
        again.get().close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aRelayParkIsCountedWhenItHappensAndNotAgainAsAnOperatorPark() throws Exception {
        String consentId = create("idem-poison", "40.00", "ix-poison");
        KafkaTemplate<String, String> kafka = Mockito.mock(KafkaTemplate.class);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.failedFuture(
                new org.apache.kafka.common.errors.RecordTooLargeException("record too large")));
        io.micrometer.core.instrument.simple.SimpleMeterRegistry meters = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        OutboxRelay countingRelay = relay(kafka, Duration.ofSeconds(5), meters);

        countingRelay.relayOnce();
        countingRelay.relayOnce();

        Map<String, Object> row = jdbc.queryForMap("select status, park_reason, park_counted from "
                + SCHEMA + ".outbox_event where aggregate_id = ?", consentId);
        assertThat(row.get("status")).isEqualTo("PARKED");
        assertThat(row.get("park_reason")).isEqualTo("payload error (relay)");
        assertThat(row.get("park_counted")).isEqualTo(true);
        assertThat(meters.get("outbox.parked.events").tag("exception", "RecordTooLargeException").counter().count())
                .isEqualTo(1.0);
        assertThat(meters.find("outbox.parked.events").tag("exception", "OperatorPark").counter()).isNull();
    }

    private OutboxRelay relay(KafkaTemplate<String, String> kafka, Duration sendTimeout) {
        return relay(kafka, sendTimeout, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    private OutboxRelay relay(KafkaTemplate<String, String> kafka, Duration sendTimeout,
                              io.micrometer.core.instrument.MeterRegistry meters) {
        return new OutboxRelay(outbox, kafka, new TransactionTemplate(transactionManager),
                new PostgresSessionRelayLock(dataSource, OutboxRelay.RELAY_LOCK_KEY), Clock.systemUTC(), meters,
                new OutboxRelay.Settings(100, sendTimeout, Duration.ofSeconds(10), Duration.ofDays(7)));
    }

    @Test
    void bearerSchemeOnTheTppPathIsUnauthorizedAndStoresNothing() throws Exception {
        mvc.perform(createRequest("idem-bearer", "5.00", "ix-bearer")
                        .with(request -> {
                            request.removeHeader("Authorization");
                            request.addHeader("Authorization", "Bearer TPP-001");
                            return request;
                        }))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("DPoP")));
        assertThat(count("pay_request")).isZero();
    }

    @Test
    void aReplayedProofIsUnauthorized() throws Exception {
        String consentId = create("idem-replay-proof", "5.00", "ix-rp");
        String url = "http://localhost/open-finance/v1/payment-consents/" + consentId;
        String proof = proof("GET", url, "TPP-001");

        mvc.perform(get("/open-finance/v1/payment-consents/{id}", consentId).header("X-FAPI-Interaction-ID", "ix-rp1")
                        .header("Authorization", "DPoP TPP-001").header("DPoP", proof))
                .andExpect(status().isOk());
        mvc.perform(get("/open-finance/v1/payment-consents/{id}", consentId).header("X-FAPI-Interaction-ID", "ix-rp2")
                        .header("Authorization", "DPoP TPP-001").header("DPoP", proof))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void interactionIdMustBeASafeToken() throws Exception {
        mvc.perform(asTpp(post("/open-finance/v1/par"))
                        .header("X-FAPI-Interaction-ID", "ix-1,ix-2 <script>")
                        .header("X-Idempotency-Key", "idem-bad")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.formatted("1.00")))
                .andExpect(status().isBadRequest());
        assertThat(count("pay_request")).isZero();
    }

    @Test
    void dpopJtiIsAcceptedOnceAcrossReplicas() {
        assertThat(dpopJti.saveJtiIfAbsent("jti-it-1", 300)).isTrue();
        assertThat(dpopJti.saveJtiIfAbsent("jti-it-1", 300)).isFalse();
    }

    @Test
    void databaseRejectsRowsThatBreakTheAggregateInvariants() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("""
                insert into sc_pay_request_to_pay.pay_request
                  (consent_id, tpp_id, debtor_id, creditor_name, amount, currency, status, requested_at, updated_at)
                values ('C-X', 'T', 'D', 'C', 1.00, 'AED', 'CONSUMED', now(), now())"""))
                .hasMessageContaining("ck_pay_request_payment_id");
    }

    private Object decide(CountDownLatch start, Callable<PayRequestResult> decision) throws InterruptedException {
        start.await();
        try {
            return decision.call();
        } catch (Exception e) {
            return e;
        }
    }

    private String create(String key, String amount, String interactionId) throws Exception {
        String response = mvc.perform(createRequest(key, amount, interactionId))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Idempotent-Replay", "false"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("Data").get("ConsentId").asText();
    }

    private MockHttpServletRequestBuilder createRequest(String key, String amount, String interactionId) {
        return asTpp(post("/open-finance/v1/par"))
                .header("X-FAPI-Interaction-ID", interactionId)
                .header("X-Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY.formatted(amount));
    }

    private static MockHttpServletRequestBuilder asTpp(MockHttpServletRequestBuilder request) {
        return request.with(dpop("TPP-001"));
    }

    /**
     * Authorization: DPoP with a fresh proof for this request's method and URL, signed by
     * TPP_KEY. The token value names the TPP; the mocked decoder turns it into a token bound
     * to TPP_KEY (cnf.jkt), so the real filter, proof validation and jti store all run.
     */
    private static RequestPostProcessor dpop(String tppId) {
        return request -> {
            request.addHeader("Authorization", "DPoP " + tppId);
            request.addHeader("DPoP", proof(request.getMethod(), request.getRequestURL().toString(), tppId));
            return request;
        };
    }

    /** A proof for {@code accessToken} (ath), the token value the mocked decoder turns into a bound token. */
    private static String proof(String method, String url, String accessToken) {
        try {
            SignedJWT proof = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.ES256).type(new JOSEObjectType("dpop+jwt"))
                            .jwk(TPP_KEY.toPublicJWK()).build(),
                    new JWTClaimsSet.Builder().jwtID(UUID.randomUUID().toString()).issueTime(new Date())
                            .claim("htm", method).claim("htu", url)
                            .claim("ath", DPoPValidationService.accessTokenHash(accessToken)).build());
            proof.sign(new ECDSASigner(TPP_KEY));
            return proof.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Jwt boundToken(String tppId) {
        try {
            return Jwt.withTokenValue(tppId).header("alg", "PS256").subject("tpp-user")
                    .claim("azp", tppId).claim("scope", "payments").claim("fbx_client_type", "open-finance-tpp").audience(List.of("svc-pay-request-to-pay"))
                    .claim("cnf", Map.of("jkt", TPP_KEY.computeThumbprint("SHA-256").toString()))
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + SCHEMA + "." + table, Long.class);
    }

    private static String recordHeader(ProducerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), java.nio.charset.StandardCharsets.UTF_8);
    }
}
