package com.enterprise.openfinance.requesttopay.infrastructure.rest;

import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestResult;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestStatus;
import com.enterprise.openfinance.requesttopay.domain.port.in.PayRequestUseCase;
import com.enterprise.openfinance.requesttopay.infrastructure.cache.IdempotencyKeyRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = PayRequestControllerDPoPIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
)
@AutoConfigureMockMvc
class PayRequestControllerDPoPIntegrationTest {
    private static final String BASE_URL = "/api/v1/pay-requests/";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PayRequestUseCase payRequestUseCase;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private DPoPNonceRepository dpopNonceRepository;

    @MockBean
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private ECKey dpopKey;

    @BeforeEach
    void setUp() throws Exception {
        dpopKey = new ECKeyGenerator(Curve.P_256).generate();
        when(dpopNonceRepository.saveJtiIfAbsent(anyString(), anyLong())).thenReturn(true);
        when(payRequestUseCase.getPayRequestStatus(any())).thenReturn(new PayRequestResult(sampleRequest(), false));
    }

    @Test
    void getPayRequestStatus_withValidDPoPAndJwt_shouldReturnOk() throws Exception {
        String consentId = "consent-123";
        String path = BASE_URL + consentId;
        String fullUrl = "http://localhost" + path;
        
        String dpopProof = DPoPTestUtils.createDPoPProof(dpopKey, HttpMethod.GET, fullUrl);
        Jwt jwtToken = DPoPTestUtils.createJwtWithCnf(dpopKey);

        when(jwtDecoder.decode(any())).thenReturn(jwtToken);
        mockMvc.perform(get(path)
                        .with(jwt().jwt(jwtToken))
                        .header("DPoP", dpopProof)
                        .header("X-FAPI-Interaction-ID", "interaction-123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getPayRequestStatus_withInvalidDPoP_shouldReturnUnauthorized() throws Exception {
        String consentId = "consent-123";
        String path = BASE_URL + consentId;
        
        // DPoP proof for a different URL
        String dpopProof = DPoPTestUtils.createDPoPProof(dpopKey, HttpMethod.GET, "http://localhost/api/v1/other");
        Jwt jwtToken = DPoPTestUtils.createJwtWithCnf(dpopKey);

        when(jwtDecoder.decode(any())).thenReturn(jwtToken);

        mockMvc.perform(get(path)
                        .with(jwt().jwt(jwtToken))
                        .header("DPoP", dpopProof)
                        .header("X-FAPI-Interaction-ID", "interaction-123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPayRequestStatus_withMismatchedCnf_shouldReturnUnauthorized() throws Exception {
        String consentId = "consent-123";
        String path = BASE_URL + consentId;
        String fullUrl = "http://localhost" + path;
        
        String dpopProof = DPoPTestUtils.createDPoPProof(dpopKey, HttpMethod.GET, fullUrl);

        // Create a JWT with a cnf claim that doesn't match the DPoP key
        ECKey otherKey = new ECKeyGenerator(Curve.P_256).generate();
        Jwt jwtToken = DPoPTestUtils.createJwtWithCnf(otherKey);

        when(jwtDecoder.decode(any())).thenReturn(jwtToken);

        mockMvc.perform(get(path)
                        .with(jwt().jwt(jwtToken))
                        .header("DPoP", dpopProof)
                        .header("X-FAPI-Interaction-ID", "interaction-123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
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
    }
}
