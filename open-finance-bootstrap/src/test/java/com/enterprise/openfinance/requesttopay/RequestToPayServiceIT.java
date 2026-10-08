package com.enterprise.openfinance.requesttopay;

import com.enterprise.openfinance.requesttopay.domain.command.CreatePayRequestCommand;
import com.enterprise.openfinance.requesttopay.domain.exception.PayRequestFinalizedException;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestResult;
import com.enterprise.openfinance.requesttopay.domain.port.in.PayRequestUseCase;
import com.enterprise.openfinance.requesttopay.infrastructure.outbox.OutboxEventJpaEntity;
import com.enterprise.openfinance.requesttopay.infrastructure.outbox.OutboxRelay;
import com.enterprise.openfinance.requesttopay.infrastructure.outbox.SpringDataOutboxRepository;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    @BeforeEach
    void cleanTables() {
        jdbc.update("delete from " + SCHEMA + ".outbox_event");
        jdbc.update("delete from " + SCHEMA + ".pay_request_idempotency");
        jdbc.update("delete from " + SCHEMA + ".pay_request");
        jdbc.update("delete from " + SCHEMA + ".dpop_proof_jti");
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
                .containsExactly("evt.pay.rtp.created.v1", "evt.pay.rtp.accepted.v1");
        assertThat(events).extracting(e -> ((Number) e.get("aggregate_version")).longValue()).containsExactly(0L, 1L);
        assertThat(events).extracting(e -> e.get("correlation_id")).containsExactly("ix-create", "ix-accept");
        JsonNode accepted = json.readTree((String) events.get(1).get("payload"));
        assertThat(accepted.get("eventType").asText()).isEqualTo("Payments.PayRequest.Accepted.v1");
        assertThat(accepted.get("producer").asText()).isEqualTo("svc-pay-request-to-pay");
        assertThat(accepted.get("data").get("amount").asText()).isEqualTo("500.00");
        assertThat(accepted.get("data").get("paymentId").asText()).isEqualTo("PAY-777");
    }

    @Test
    void anotherTppCannotReadOrDecideTheRequest() throws Exception {
        String consentId = create("idem-own", "10.00", "ix-own");

        mvc.perform(get("/open-finance/v1/payment-consents/{id}", consentId)
                        .with(dpop("TPP-OTHER"))
                        .header("X-FAPI-Interaction-ID", "ix"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/open-finance/v1/payment-consents/{id}/reject", consentId)
                        .with(dpop("TPP-OTHER"))
                        .header("X-FAPI-Interaction-ID", "ix")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("select status from " + SCHEMA + ".pay_request where consent_id = ?",
                String.class, consentId)).isEqualTo("AWAITING_AUTHORISATION");
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
        Future<Object> accept = pool.submit(() -> decide(start, () -> useCase.acceptPayRequest(consentId, "TPP-001", "PAY-1", "ix-a")));
        Future<Object> reject = pool.submit(() -> decide(start, () -> useCase.rejectPayRequest(consentId, "TPP-001", "ix-r")));
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
        OutboxRelay relay = new OutboxRelay(outbox, kafka, new TransactionTemplate(transactionManager),
                Clock.systemUTC(), 100, 10, Duration.ofSeconds(5), Duration.ofDays(7));

        assertThat(relay.relayOnce()).isEqualTo(1);
        assertThat(relay.relayOnce()).isZero();

        org.mockito.ArgumentCaptor<ProducerRecord<String, String>> sent = org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        Mockito.verify(kafka).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo("evt.pay.rtp.created.v1");
        assertThat(sent.getValue().key()).isEqualTo(consentId);
        assertThat(outbox.countByStatus(OutboxEventJpaEntity.Status.PUBLISHED)).isEqualTo(1);
        assertThat(outbox.countByStatus(OutboxEventJpaEntity.Status.PENDING)).isZero();
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
        String proof = proof("GET", url);

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
            request.addHeader("DPoP", proof(request.getMethod(), request.getRequestURL().toString()));
            return request;
        };
    }

    private static String proof(String method, String url) {
        try {
            SignedJWT proof = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.ES256).type(new JOSEObjectType("dpop+jwt"))
                            .jwk(TPP_KEY.toPublicJWK()).build(),
                    new JWTClaimsSet.Builder().jwtID(UUID.randomUUID().toString()).issueTime(new Date())
                            .claim("htm", method).claim("htu", url).build());
            proof.sign(new ECDSASigner(TPP_KEY));
            return proof.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Jwt boundToken(String tppId) {
        try {
            return Jwt.withTokenValue(tppId).header("alg", "PS256").subject("tpp-user")
                    .claim("azp", tppId).audience(List.of("svc-pay-request-to-pay"))
                    .claim("cnf", Map.of("jkt", TPP_KEY.computeThumbprint("SHA-256").toString()))
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + SCHEMA + "." + table, Long.class);
    }
}
