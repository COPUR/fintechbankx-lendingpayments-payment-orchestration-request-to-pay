package com.enterprise.openfinance.requesttopay.infrastructure.rest;

import com.enterprise.openfinance.requesttopay.domain.model.PayRequest;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestResult;
import com.enterprise.openfinance.requesttopay.domain.model.PayRequestStatus;
import com.enterprise.openfinance.requesttopay.domain.port.in.PayRequestUseCase;
import com.enterprise.openfinance.requesttopay.infrastructure.security.DPoPNonceRepository;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TLS ends at the Istio ingress gateway, which overwrites X-Forwarded-Proto/Host/Port with the
 * public route (platform contract, "Forwarded headers (htu)"). With
 * server.forward-headers-strategy=framework the DPoP htu is the URL the TPP called, not the
 * pod-internal one. X-Forwarded-For plays no part.
 */
@SpringBootTest(
        classes = PayRequestControllerDPoPIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
                "server.forward-headers-strategy=framework"
        }
)
@AutoConfigureMockMvc
class PayRequestDpopForwardedHeadersTest {

    private static final String PATH = "/open-finance/v1/payment-consents/consent-123";
    private static final String PUBLIC_URL = "https://api.fintechbankx.example" + PATH;

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
        when(jwtDecoder.decode(any())).thenReturn(DPoPTestUtils.createJwtWithCnf(dpopKey, "TPP-001"));
        when(payRequestUseCase.getPayRequestStatus(any())).thenReturn(new PayRequestResult(new PayRequest(
                "consent-123", "TPP-001", "PSU-001", "Utilities Co", new BigDecimal("500.00"), "AED",
                PayRequestStatus.AWAITING_AUTHORISATION, Instant.parse("2026-02-10T10:00:00Z"),
                Instant.parse("2026-02-10T10:00:00Z"), null), false));
    }

    @Test
    void htuIsTheGatewayUrlFromXForwardedProtoHostAndPort() throws Exception {
        mockMvc.perform(viaGateway(DPoPTestUtils.createDPoPProof(dpopKey, HttpMethod.GET, PUBLIC_URL)))
                .andExpect(status().isOk());
    }

    @Test
    void proofForThePodInternalUrlIsUnauthorizedBehindTheGateway() throws Exception {
        mockMvc.perform(viaGateway(DPoPTestUtils.createDPoPProof(dpopKey, HttpMethod.GET, "http://localhost" + PATH)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void proofForAnotherPublicHostIsUnauthorized() throws Exception {
        String otherHost = "https://evil.example" + PATH;
        mockMvc.perform(viaGateway(DPoPTestUtils.createDPoPProof(dpopKey, HttpMethod.GET, otherHost)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void xForwardedForDoesNotInfluenceTheHtu() throws Exception {
        mockMvc.perform(viaGateway(DPoPTestUtils.createDPoPProof(dpopKey, HttpMethod.GET, PUBLIC_URL))
                        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1"))
                .andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder viaGateway(String proof) {
        return get(PATH)
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "api.fintechbankx.example")
                .header("X-Forwarded-Port", "443")
                .header("Authorization", "DPoP token")
                .header("DPoP", proof)
                .header("X-FAPI-Interaction-ID", "ix-forwarded");
    }
}
