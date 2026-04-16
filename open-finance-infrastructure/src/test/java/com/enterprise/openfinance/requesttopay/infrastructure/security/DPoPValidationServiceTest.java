package com.enterprise.openfinance.requesttopay.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;

import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DPoPValidationServiceTest {

    @Mock
    private DPoPNonceRepository dpopNonceRepository;

    private DPoPValidationService dpopValidationService;

    private ECKey ecKey;

    @BeforeEach
    void setUp() throws Exception {
        dpopValidationService = new DPoPValidationService(dpopNonceRepository);
        ecKey = new ECKeyGenerator(Curve.P_256).generate();
    }

    private String createDPoPProof(HttpMethod method, URI uri, String jti, Instant iat) throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(DPoPValidationService.DPOP_JWT_TYPE)
                .jwk(ecKey.toPublicJWK())
                .build();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .jwtID(jti)
                .issueTime(Date.from(iat))
                .claim("htm", method.name())
                .claim("htu", uri.toString())
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(new ECDSASigner(ecKey));
        return signedJWT.serialize();
    }

    @Test
    void validateDPoPProof_withValidProof_shouldSucceed() throws Exception {
        HttpMethod method = HttpMethod.POST;
        URI uri = new URI("https://api.example.com/pay-requests");
        String jti = UUID.randomUUID().toString();
        Instant iat = Instant.now();
        String dpopProof = createDPoPProof(method, uri, jti, iat);

        when(dpopNonceRepository.saveJtiIfAbsent(jti, 300)).thenReturn(true);

        assertDoesNotThrow(() -> dpopValidationService.validateDPoPProof(dpopProof, method, uri));
    }

    @Test
    void validateDPoPProof_withMissingHeader_shouldThrowException() {
        DPoPValidationException ex = assertThrows(DPoPValidationException.class,
                () -> dpopValidationService.validateDPoPProof(null, HttpMethod.POST, new URI("https://api.example.com")));
        assertEquals("DPoP header is missing or empty", ex.getMessage());
    }

    @Test
    void validateDPoPProof_withReplayedJti_shouldThrowException() throws Exception {
        HttpMethod method = HttpMethod.POST;
        URI uri = new URI("https://api.example.com/pay-requests");
        String jti = UUID.randomUUID().toString();
        Instant iat = Instant.now();
        String dpopProof = createDPoPProof(method, uri, jti, iat);

        when(dpopNonceRepository.saveJtiIfAbsent(jti, 300)).thenReturn(false);

        DPoPValidationException ex = assertThrows(DPoPValidationException.class,
                () -> dpopValidationService.validateDPoPProof(dpopProof, method, uri));
        assertEquals("DPoP JWT 'jti' replay detected", ex.getMessage());
    }

    @Test
    void validateDPoPProof_withMismatchedHtm_shouldThrowException() throws Exception {
        HttpMethod proofMethod = HttpMethod.POST;
        HttpMethod requestMethod = HttpMethod.GET;
        URI uri = new URI("https://api.example.com/pay-requests");
        String jti = UUID.randomUUID().toString();
        Instant iat = Instant.now();
        String dpopProof = createDPoPProof(proofMethod, uri, jti, iat);

        when(dpopNonceRepository.saveJtiIfAbsent(anyString(), anyLong())).thenReturn(true);

        DPoPValidationException ex = assertThrows(DPoPValidationException.class,
                () -> dpopValidationService.validateDPoPProof(dpopProof, requestMethod, uri));
        assertTrue(ex.getMessage().contains("DPoP JWT 'htm' claim mismatch"));
    }

    @Test
    void validateDPoPProof_withMismatchedHtu_shouldThrowException() throws Exception {
        HttpMethod method = HttpMethod.POST;
        URI proofUri = new URI("https://api.example.com/pay-requests");
        URI requestUri = new URI("https://api.example.com/other");
        String jti = UUID.randomUUID().toString();
        Instant iat = Instant.now();
        String dpopProof = createDPoPProof(method, proofUri, jti, iat);

        when(dpopNonceRepository.saveJtiIfAbsent(anyString(), anyLong())).thenReturn(true);

        DPoPValidationException ex = assertThrows(DPoPValidationException.class,
                () -> dpopValidationService.validateDPoPProof(dpopProof, method, requestUri));
        assertTrue(ex.getMessage().contains("DPoP JWT 'htu' claim mismatch"));
    }
}
