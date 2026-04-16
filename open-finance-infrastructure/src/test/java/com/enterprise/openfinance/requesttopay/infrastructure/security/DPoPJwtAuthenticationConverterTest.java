package com.enterprise.openfinance.requesttopay.infrastructure.security;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DPoPJwtAuthenticationConverterTest {

    private DPoPJwtAuthenticationConverter converter;

    @Mock
    private JwtAuthenticationConverter delegateConverter;

    @Mock
    private HttpServletRequest request;

    private ECKey dpopJwk;
    private String dpopJkt;

    @BeforeEach
    void setUp() throws Exception {
        converter = new DPoPJwtAuthenticationConverter(delegateConverter);
        dpopJwk = new ECKeyGenerator(Curve.P_256).generate();
        dpopJkt = dpopJwk.computeThumbprint("SHA-256").toString();

        // Mock RequestContextHolder to return our mock request
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
    }

    @Test
    void convert_withValidDPoPAndCnf_shouldSucceed() {
        // Given
        Jwt jwt = mock(Jwt.class);
        Map<String, Object> cnfClaim = new HashMap<>();
        cnfClaim.put("jkt", dpopJkt);
        when(jwt.getClaimAsMap("cnf")).thenReturn(cnfClaim);

        when(request.getAttribute(DPoPSecurityAspect.DPOP_JWK_REQUEST_ATTRIBUTE)).thenReturn(dpopJwk);

        JwtAuthenticationToken expectedToken = new JwtAuthenticationToken(jwt, Collections.emptyList());
        when(delegateConverter.convert(jwt)).thenReturn(expectedToken);

        // When
        AbstractAuthenticationToken result = converter.convert(jwt);

        // Then
        assertNotNull(result);
        assertEquals(expectedToken, result);
        verify(delegateConverter, times(1)).convert(jwt);
    }

    @Test
    void convert_withMissingDPoPJwkInRequest_shouldThrowException() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(request.getAttribute(DPoPSecurityAspect.DPOP_JWK_REQUEST_ATTRIBUTE)).thenReturn(null);

        // When/Then
        DPoPValidationException ex = assertThrows(DPoPValidationException.class, () -> converter.convert(jwt));
        assertEquals("DPoP JWK not found in request attributes. Ensure @DPoPSecured is used.", ex.getMessage());
        verifyNoInteractions(delegateConverter);
    }

    @Test
    void convert_withMissingCnfClaimInJwt_shouldThrowException() {
        // Given
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsMap("cnf")).thenReturn(null); // Missing cnf claim

        when(request.getAttribute(DPoPSecurityAspect.DPOP_JWK_REQUEST_ATTRIBUTE)).thenReturn(dpopJwk);

        // When/Then
        DPoPValidationException ex = assertThrows(DPoPValidationException.class, () -> converter.convert(jwt));
        assertEquals("Access token 'cnf' claim with 'jkt' is missing", ex.getMessage());
        verifyNoInteractions(delegateConverter);
    }

    @Test
    void convert_withMissingJktInCnfClaim_shouldThrowException() {
        // Given
        Jwt jwt = mock(Jwt.class);
        Map<String, Object> cnfClaim = new HashMap<>();
        cnfClaim.put("other_claim", "value"); // cnf claim without jkt
        when(jwt.getClaimAsMap("cnf")).thenReturn(cnfClaim);

        when(request.getAttribute(DPoPSecurityAspect.DPOP_JWK_REQUEST_ATTRIBUTE)).thenReturn(dpopJwk);

        // When/Then
        DPoPValidationException ex = assertThrows(DPoPValidationException.class, () -> converter.convert(jwt));
        assertEquals("Access token 'cnf' claim with 'jkt' is missing", ex.getMessage());
        verifyNoInteractions(delegateConverter);
    }

    @Test
    void convert_withMismatchedJkt_shouldThrowException() throws Exception {
        // Given
        ECKey otherJwk = new ECKeyGenerator(Curve.P_256).generate();
        String mismatchedJkt = otherJwk.computeThumbprint("SHA-256").toString();

        Jwt jwt = mock(Jwt.class);
        Map<String, Object> cnfClaim = new HashMap<>();
        cnfClaim.put("jkt", mismatchedJkt); // Mismatched jkt
        when(jwt.getClaimAsMap("cnf")).thenReturn(cnfClaim);

        when(request.getAttribute(DPoPSecurityAspect.DPOP_JWK_REQUEST_ATTRIBUTE)).thenReturn(dpopJwk);

        // When/Then
        DPoPValidationException ex = assertThrows(DPoPValidationException.class, () -> converter.convert(jwt));
        assertEquals("DPoP JWK thumbprint does not match access token 'cnf' claim", ex.getMessage());
        verifyNoInteractions(delegateConverter);
    }
}
