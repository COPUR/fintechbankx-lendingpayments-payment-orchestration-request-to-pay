package com.enterprise.openfinance.requesttopay.infrastructure.rest;

import com.enterprise.openfinance.requesttopay.domain.port.in.PayRequestUseCase;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import wiremock.com.fasterxml.jackson.databind.ObjectMapper;
import wiremock.com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PayRequestControllerDPoPIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PayRequestUseCase payRequestUseCase;

    @MockBean
    private JwtDecoder jwtDecoder; // Mock the decoder to control JWT validation

    private ECKey dpopKey;
    private static WireMockServer wireMockServer;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(8080);
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() throws Exception {
        dpopKey = new ECKeyGenerator(Curve.P_256).generate();
        setupJwksEndpoint();
    }

    private void setupJwksEndpoint() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode jwks = mapper.createObjectNode();
        jwks.putPOJO("keys", Collections.singletonList(dpopKey.toPublicJWK().toJSONObject()));

        wireMockServer.stubFor(get(urlEqualTo("/jwks"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(jwks.toString())));
    }

    private String createDPoPProof(HttpMethod method, String url) throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(com.enterprise.openfinance.requesttopay.infrastructure.security.DPoPValidationService.DPOP_JWT_TYPE)
                .jwk(dpopKey.toPublicJWK())
                .build();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(Instant.now()))
                .claim("htm", method.name())
                .claim("htu", url)
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(new ECDSASigner(dpopKey));
        return signedJWT.serialize();
    }

    private Jwt createJwtWithCnf() throws Exception {
        String jkt = dpopKey.computeThumbprint("SHA-256").toString();
        Map<String, Object> cnf = Collections.singletonMap("jkt", jkt);

        return Jwt.withTokenValue("token")
                .header("alg", "ES256")
                .claim("sub", "user")
                .claim("scope", "payments")
                .claim("cnf", cnf)
                .build();
    }

    @Test
    void getPayRequestStatus_withValidDPoPAndJwt_shouldReturnOk() throws Exception {
        String consentId = "consent-123";
        String url = "http://localhost/api/v1/pay-requests/" + consentId;
        String dpopProof = createDPoPProof(HttpMethod.GET, url);
        Jwt jwt = createJwtWithCnf();

        when(jwtDecoder.decode(any())).thenReturn(jwt);

        mockMvc.perform(get(url)
                        .with(jwt().jwt(jwt))
                        .header("DPoP", dpopProof)
                        .header("X-FAPI-Interaction-ID", "interaction-123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void getPayRequestStatus_withInvalidDPoP_shouldReturnUnauthorized() throws Exception {
        String consentId = "consent-123";
        String url = "http://localhost/api/v1/pay-requests/" + consentId;
        String dpopProof = createDPoPProof(HttpMethod.GET, "http://localhost/api/v1/other");
        Jwt jwt = createJwtWithCnf();

        when(jwtDecoder.decode(any())).thenReturn(jwt);

        mockMvc.perform(get(url)
                        .with(jwt().jwt(jwt))
                        .header("DPoP", dpopProof)
                        .header("X-FAPI-Interaction-ID", "interaction-123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPayRequestStatus_withMismatchedCnf_shouldReturnUnauthorized() throws Exception {
        String consentId = "consent-123";
        String url = "http://localhost/api/v1/pay-requests/" + consentId;
        String dpopProof = createDPoPProof(HttpMethod.GET, url);

        // Create a JWT with a cnf claim that doesn't match the DPoP key
        ECKey otherKey = new ECKeyGenerator(Curve.P_256).generate();
        String otherJkt = otherKey.computeThumbprint("SHA-256").toString();
        Map<String, Object> cnf = Collections.singletonMap("jkt", otherJkt);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "ES256")
                .claim("sub", "user")
                .claim("scope", "payments")
                .claim("cnf", cnf)
                .build();

        when(jwtDecoder.decode(any())).thenReturn(jwt);

        mockMvc.perform(get(url)
                        .with(jwt().jwt(jwt))
                        .header("DPoP", dpopProof)
                        .header("X-FAPI-Interaction-ID", "interaction-123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
