package com.enterprise.openfinance.requesttopay.infrastructure.rest;

import com.enterprise.openfinance.requesttopay.domain.exception.PayRequestNotFoundException;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestResult;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestStatus;
import com.enterprise.openfinance.requesttopay.domain.port.in.PayRequestUseCase;
import com.enterprise.openfinance.requesttopay.infrastructure.config.SecurityConfig;
import com.enterprise.openfinance.requesttopay.infrastructure.security.DPoPNonceRepository;
import com.enterprise.openfinance.requesttopay.infrastructure.security.DPoPSecurityAspect;
import com.enterprise.openfinance.requesttopay.infrastructure.security.DPoPValidationService;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TPP endpoints under /open-finance/v1 require the DPoP scheme, a valid proof
 * (htm, htu, iat, unique jti) and a token whose cnf.jkt matches the proof key
 * (platform contract: "DPoP applies by caller, not by namespace").
 */
@SpringBootTest(
        classes = PayRequestControllerDPoPIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
)
@AutoConfigureMockMvc
class PayRequestControllerDPoPIntegrationTest {

    private static final String STATUS_PATH = "/open-finance/v1/payment-consents/consent-123";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PayRequestUseCase payRequestUseCase;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private DPoPNonceRepository dpopNonceRepository;

    private ECKey dpopKey;

    @BeforeEach
    void setUp() throws Exception {
        dpopKey = new ECKeyGenerator(Curve.P_256).generate();
        when(dpopNonceRepository.saveJtiIfAbsent(anyString(), anyLong())).thenReturn(true);
        when(payRequestUseCase.getPayRequestStatus(any())).thenReturn(new PayRequestResult(sampleRequest(), false));
    }

    @Test
    void dpopSchemeWithBoundTokenAndValidProofIsServed() throws Exception {
        token(DPoPTestUtils.createJwtWithCnf(dpopKey, "TPP-001"));

        mockMvc.perform(withProof(get(STATUS_PATH).header("Authorization", "DPoP token"), STATUS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Links.Self").value(STATUS_PATH));
    }

    @Test
    void bearerSchemeIsUnauthorizedEvenWithAValidProof() throws Exception {
        token(DPoPTestUtils.createJwtWithCnf(dpopKey, "TPP-001"));

        mockMvc.perform(withProof(get(STATUS_PATH).header("Authorization", "Bearer token"), STATUS_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("DPoP")));
    }

    @Test
    void missingProofIsUnauthorized() throws Exception {
        token(DPoPTestUtils.createJwtWithCnf(dpopKey, "TPP-001"));

        mockMvc.perform(get(STATUS_PATH).header("Authorization", "DPoP token")
                        .header("X-FAPI-Interaction-ID", "interaction-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("DPoP")))
                .andExpect(jsonPath("$.code").value("DPOP_VALIDATION_FAILED"));
    }

    @Test
    void tokenWithoutCnfIsUnauthorized() throws Exception {
        token(Jwt.withTokenValue("token").header("alg", "RS256").claim("sub", "tpp-user")
                .claim("azp", "TPP-001").build());

        mockMvc.perform(withProof(get(STATUS_PATH).header("Authorization", "DPoP token"), STATUS_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void proofFromAnotherKeyThanTheTokenBindingIsUnauthorized() throws Exception {
        ECKey otherKey = new ECKeyGenerator(Curve.P_256).generate();
        token(DPoPTestUtils.createJwtWithCnf(otherKey, "TPP-001"));

        mockMvc.perform(withProof(get(STATUS_PATH).header("Authorization", "DPoP token"), STATUS_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void proofForAnotherUrlIsUnauthorized() throws Exception {
        token(DPoPTestUtils.createJwtWithCnf(dpopKey, "TPP-001"));

        mockMvc.perform(withProof(get(STATUS_PATH).header("Authorization", "DPoP token"),
                        "/open-finance/v1/payment-consents/other"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void proofBoundToAnotherAccessTokenIsUnauthorized() throws Exception {
        token(DPoPTestUtils.createJwtWithCnf(dpopKey, "TPP-001"));
        String proof = DPoPTestUtils.createDPoPProof(dpopKey, HttpMethod.GET, "http://localhost" + STATUS_PATH,
                "a-different-access-token");

        mockMvc.perform(get(STATUS_PATH).header("Authorization", "DPoP token").header("DPoP", proof)
                        .header("X-FAPI-Interaction-ID", "interaction-123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void replayedProofIsUnauthorized() throws Exception {
        token(DPoPTestUtils.createJwtWithCnf(dpopKey, "TPP-001"));
        when(dpopNonceRepository.saveJtiIfAbsent(anyString(), anyLong())).thenReturn(false);

        mockMvc.perform(withProof(get(STATUS_PATH).header("Authorization", "DPoP token"), STATUS_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void financialIdHeaderNamingAnotherTppIsForbidden() throws Exception {
        token(DPoPTestUtils.createJwtWithCnf(dpopKey, "TPP-001"));

        mockMvc.perform(withProof(get(STATUS_PATH).header("Authorization", "DPoP token"), STATUS_PATH)
                        .header("x-fapi-financial-id", "TPP-OTHER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenWithoutClientIdentityIsForbidden() throws Exception {
        token(DPoPTestUtils.createJwtWithCnf(dpopKey, null));

        mockMvc.perform(withProof(get(STATUS_PATH).header("Authorization", "DPoP token"), STATUS_PATH))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenWithTheAudienceButWithoutThePaymentsScopeIsForbidden() throws Exception {
        token(DPoPTestUtils.createJwtWithCnf(dpopKey, "TPP-001", "accounts openid", java.util.Map.of()));

        mockMvc.perform(withProof(get(STATUS_PATH).header("Authorization", "DPoP token"), STATUS_PATH))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenWithoutAnyScopeIsForbidden() throws Exception {
        token(DPoPTestUtils.createJwtWithCnf(dpopKey, "TPP-001", null, java.util.Map.of()));

        mockMvc.perform(withProof(get(STATUS_PATH).header("Authorization", "DPoP token"), STATUS_PATH))
                .andExpect(status().isForbidden());
    }

    @Test
    void serviceClientTokenIsForbiddenEvenWithTheScope() throws Exception {
        token(DPoPTestUtils.createJwtWithCnf(dpopKey, "svc-pay-bulk-orchestration"));

        mockMvc.perform(withProof(get(STATUS_PATH).header("Authorization", "DPoP token"), STATUS_PATH))
                .andExpect(status().isForbidden());
    }

    @Test
    void firstPartyChannelTokenIsForbidden() throws Exception {
        token(DPoPTestUtils.createJwtWithCnf(dpopKey, "fintechbankx-mobile"));

        mockMvc.perform(withProof(get(STATUS_PATH).header("Authorization", "DPoP token"), STATUS_PATH))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientTypeClaimOtherThanOpenFinanceTppIsForbidden() throws Exception {
        token(DPoPTestUtils.createJwtWithCnf(dpopKey, "TPP-001", "payments",
                java.util.Map.of("fbx_client_type", "first-party-public")));

        mockMvc.perform(withProof(get(STATUS_PATH).header("Authorization", "DPoP token"), STATUS_PATH))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientTypeClaimOfATppIsServed() throws Exception {
        token(DPoPTestUtils.createJwtWithCnf(dpopKey, "TPP-001", "payments",
                java.util.Map.of("fbx_client_type", "open-finance-tpp")));

        mockMvc.perform(withProof(get(STATUS_PATH).header("Authorization", "DPoP token"), STATUS_PATH))
                .andExpect(status().isOk());
    }

    @Test
    void aTokenFromAnUnknownClientWithoutTheClientTypeClaimIsForbidden() throws Exception {
        // TPP-UNKNOWN is neither denied nor in requesttopay.security.tpp.allowed-clients.
        token(DPoPTestUtils.createJwtWithCnf(dpopKey, "TPP-UNKNOWN"));

        mockMvc.perform(withProof(get(STATUS_PATH).header("Authorization", "DPoP token"), STATUS_PATH))
                .andExpect(status().isForbidden());
    }

    @Test
    void aListedClientWithoutTheClientTypeClaimIsServed() throws Exception {
        token(DPoPTestUtils.createJwtWithCnf(dpopKey, "TPP-001"));

        mockMvc.perform(withProof(get(STATUS_PATH).header("Authorization", "DPoP token"), STATUS_PATH))
                .andExpect(status().isOk());
    }

    @Test
    void psuIdThatIsNotAnOpaqueReferenceIsAnInvalidRequest() throws Exception {
        token(DPoPTestUtils.createJwtWithCnf(dpopKey, "TPP-001"));
        String path = "/open-finance/v1/par";

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                        .header("Authorization", "DPoP token")
                        .header("DPoP", DPoPTestUtils.createDPoPProof(dpopKey, HttpMethod.POST, "http://localhost" + path))
                        .header("X-FAPI-Interaction-ID", "interaction-123")
                        .header("X-Idempotency-Key", "idem-psu")
                        .contentType("application/json")
                        .content("""
                                {"Data": {"PsuId": "jane.doe@example.com", "CreditorName": "Utilities Co",
                                          "InstructedAmount": {"Amount": "10.00", "Currency": "AED"}}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        org.mockito.Mockito.verify(payRequestUseCase, org.mockito.Mockito.never()).createPayRequest(any());
    }

    @Test
    void readingAnotherTppsPayRequestIsNotFound() throws Exception {
        when(payRequestUseCase.getPayRequestStatus(any()))
                .thenThrow(new PayRequestNotFoundException(PayRequestNotFoundException.Reason.OTHER_TPP));
        token(DPoPTestUtils.createJwtWithCnf(dpopKey, "TPP-OTHER"));

        mockMvc.perform(withProof(get(STATUS_PATH).header("Authorization", "DPoP token"), STATUS_PATH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Pay request not found"));
    }

    @Test
    void requestWithoutTokenGetsADpopChallenge() throws Exception {
        mockMvc.perform(get(STATUS_PATH).header("X-FAPI-Interaction-ID", "interaction-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("DPoP")));
    }

    @Test
    void oldInternalPrefixAndUnknownPathsAreDenied() throws Exception {
        token(DPoPTestUtils.createJwtWithCnf(dpopKey, "TPP-001"));
        String oldPath = "/api/v1/pay-requests/consent-123";

        mockMvc.perform(withProof(get(oldPath).header("Authorization", "DPoP token"), oldPath))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/internal/anything").header("Authorization", "DPoP token"))
                .andExpect(status().isForbidden());
    }

    private void token(Jwt jwt) {
        when(jwtDecoder.decode(any())).thenReturn(jwt);
    }

    private MockHttpServletRequestBuilder withProof(MockHttpServletRequestBuilder request, String proofPath)
            throws Exception {
        return request
                .header("DPoP", DPoPTestUtils.createDPoPProof(dpopKey, HttpMethod.GET, "http://localhost" + proofPath))
                .header("X-FAPI-Interaction-ID", "interaction-123");
    }

    private static PayRequest sampleRequest() {
        return new PayRequest(
                "consent-123",
                "TPP-001",
                "PSU-001",
                "Utilities Co",
                new BigDecimal("500.00"),
                "AED",
                PayRequestStatus.AWAITING_AUTHORISATION,
                Instant.parse("2026-02-10T10:00:00Z"),
                Instant.parse("2026-02-10T10:00:00Z"),
                null
        );
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            MongoAutoConfiguration.class,
            MongoDataAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class
    })
    @Import({
            PayRequestController.class,
            PayRequestExceptionHandler.class,
            DPoPSecurityAspect.class,
            DPoPValidationService.class,
            SecurityConfig.class
    })
    static class TestApplication {
        @org.springframework.context.annotation.Bean
        java.time.Clock clock() {
            return java.time.Clock.systemUTC();
        }
    }
}
