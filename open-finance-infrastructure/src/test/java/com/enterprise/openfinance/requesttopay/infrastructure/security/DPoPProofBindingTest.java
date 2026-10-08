package com.enterprise.openfinance.requesttopay.infrastructure.security;

import com.nimbusds.jose.JOSEObjectType;
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
import org.springframework.http.HttpMethod;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RFC 9449 section 4.3 and 7: the proof is bound to the access token through ath, its iat
 * window is narrow, and a jti is only consumed by a proof that passed every other check and
 * stays remembered until the proof can no longer be accepted.
 */
class DPoPProofBindingTest {

    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");
    private static final URI URL = URI.create("https://api.example.com/open-finance/v1/payment-consents/C-1");
    private static final String ACCESS_TOKEN = "eyJhbGciOiJQUzI1NiJ9.payload.signature";

    private final DPoPNonceRepository nonces = mock(DPoPNonceRepository.class);
    private final DPoPValidationService service =
            new DPoPValidationService(nonces, Clock.fixed(NOW, ZoneOffset.UTC));
    private ECKey key;

    @BeforeEach
    void setUp() throws Exception {
        key = new ECKeyGenerator(Curve.P_256).generate();
        when(nonces.saveJtiIfAbsent(anyString(), anyLong())).thenReturn(true);
    }

    @Test
    void athOfTheAccessTokenIsAccepted() throws Exception {
        String proof = proof("GET", URL.toString(), NOW, ath(ACCESS_TOKEN));

        assertThat(service.validateDPoPProof(proof, HttpMethod.GET, URL, ACCESS_TOKEN)).isNotNull();
    }

    @Test
    void missingAthIsRejected() throws Exception {
        String proof = proof("GET", URL.toString(), NOW, null);

        assertThatThrownBy(() -> service.validateDPoPProof(proof, HttpMethod.GET, URL, ACCESS_TOKEN))
                .isInstanceOf(DPoPValidationException.class).hasMessageContaining("ath");
        verify(nonces, never()).saveJtiIfAbsent(anyString(), anyLong());
    }

    @Test
    void athOfAnotherTokenIsRejected() throws Exception {
        String proof = proof("GET", URL.toString(), NOW, ath("another-token"));

        assertThatThrownBy(() -> service.validateDPoPProof(proof, HttpMethod.GET, URL, ACCESS_TOKEN))
                .isInstanceOf(DPoPValidationException.class).hasMessageContaining("ath");
        verify(nonces, never()).saveJtiIfAbsent(anyString(), anyLong());
    }

    @Test
    void iatMoreThanSixtySecondsAheadIsRejected() throws Exception {
        String proof = proof("GET", URL.toString(), NOW.plusSeconds(61), ath(ACCESS_TOKEN));

        assertThatThrownBy(() -> service.validateDPoPProof(proof, HttpMethod.GET, URL, ACCESS_TOKEN))
                .isInstanceOf(DPoPValidationException.class).hasMessageContaining("future");
        verify(nonces, never()).saveJtiIfAbsent(anyString(), anyLong());
    }

    @Test
    void jtiOfAProofNearTheFutureLimitIsKeptUntilThatProofExpires() throws Exception {
        String jti = UUID.randomUUID().toString();
        String proof = proof(jti, "GET", URL.toString(), NOW.plusSeconds(55), ath(ACCESS_TOKEN));

        service.validateDPoPProof(proof, HttpMethod.GET, URL, ACCESS_TOKEN);

        // accepted until iat + 300 s max age + 60 s skew = now + 415 s
        verify(nonces).saveJtiIfAbsent(eq(jti), eq(415L));
    }

    @Test
    void jtiOfAnOlderProofIsKeptForMaxAgePlusSkewFromNow() throws Exception {
        String jti = UUID.randomUUID().toString();
        String proof = proof(jti, "GET", URL.toString(), NOW.minusSeconds(100), ath(ACCESS_TOKEN));

        service.validateDPoPProof(proof, HttpMethod.GET, URL, ACCESS_TOKEN);

        verify(nonces).saveJtiIfAbsent(eq(jti), eq(360L));
    }

    @Test
    void aProofForAnotherMethodDoesNotUseUpItsJti() throws Exception {
        String proof = proof("POST", URL.toString(), NOW, ath(ACCESS_TOKEN));

        assertThatThrownBy(() -> service.validateDPoPProof(proof, HttpMethod.GET, URL, ACCESS_TOKEN))
                .isInstanceOf(DPoPValidationException.class).hasMessageContaining("htm");
        verify(nonces, never()).saveJtiIfAbsent(anyString(), anyLong());
    }

    @Test
    void aProofForAnotherUrlDoesNotUseUpItsJtiAndDoesNotEchoTheUrl() throws Exception {
        String proof = proof("GET", "https://api.example.com/open-finance/v1/payment-consents/C-2", NOW,
                ath(ACCESS_TOKEN));

        assertThatThrownBy(() -> service.validateDPoPProof(proof, HttpMethod.GET, URL, ACCESS_TOKEN))
                .isInstanceOf(DPoPValidationException.class).hasMessageContaining("htu")
                .message().doesNotContain("C-1").doesNotContain("C-2");
        verify(nonces, never()).saveJtiIfAbsent(anyString(), anyLong());
    }

    @Test
    void anExpiredProofDoesNotUseUpItsJti() throws Exception {
        String proof = proof("GET", URL.toString(), NOW.minusSeconds(301), ath(ACCESS_TOKEN));

        assertThatThrownBy(() -> service.validateDPoPProof(proof, HttpMethod.GET, URL, ACCESS_TOKEN))
                .isInstanceOf(DPoPValidationException.class).hasMessageContaining("too old");
        verify(nonces, never()).saveJtiIfAbsent(anyString(), anyLong());
    }

    static String ath(String accessToken) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(accessToken.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private String proof(String htm, String htu, Instant iat, String ath) throws Exception {
        return proof(UUID.randomUUID().toString(), htm, htu, iat, ath);
    }

    private String proof(String jti, String htm, String htu, Instant iat, String ath) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().jwtID(jti).issueTime(Date.from(iat))
                .claim("htm", htm).claim("htu", htu);
        if (ath != null) {
            claims.claim("ath", ath);
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(new JOSEObjectType("dpop+jwt")).jwk(key.toPublicJWK()).build(), claims.build());
        jwt.sign(new ECDSASigner(key));
        return jwt.serialize();
    }
}
