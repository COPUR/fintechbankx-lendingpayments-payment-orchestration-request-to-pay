package com.enterprise.openfinance.requesttopay.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class DPoPRequestVerifierTest {

    private final DPoPValidationService validationService = mock(DPoPValidationService.class);
    private final DPoPRequestVerifier verifier = new DPoPRequestVerifier(validationService);

    @Test
    void bearerSchemeIsRefusedBeforeTheProofIsLookedAt() {
        MockHttpServletRequest request = request("Bearer t");
        request.addHeader("DPoP", "proof");

        assertThatThrownBy(() -> verifier.verify(request, boundToken()))
                .isInstanceOf(DPoPValidationException.class)
                .hasMessage("Authorization scheme must be DPoP");
        verifyNoInteractions(validationService);
    }

    @Test
    void missingProofIsRefused() {
        assertThatThrownBy(() -> verifier.verify(request("DPoP t"), boundToken()))
                .isInstanceOf(DPoPValidationException.class)
                .hasMessage("DPoP proof is required");
        verifyNoInteractions(validationService);
    }

    @Test
    void missingAuthorizationHeaderIsRefused() {
        assertThatThrownBy(() -> verifier.verify(request(null), boundToken()))
                .isInstanceOf(DPoPValidationException.class)
                .hasMessage("Authorization scheme must be DPoP");
    }

    private static MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/open-finance/v1/payment-consents/c-1");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    private static JwtAuthenticationToken boundToken() {
        return new JwtAuthenticationToken(Jwt.withTokenValue("t").header("alg", "RS256")
                .claim("azp", "TPP-001").claim("cnf", Map.of("jkt", "thumb")).build());
    }
}
