package com.enterprise.openfinance.requesttopay.infrastructure.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Each rejected proof fails for its own reason (RFC 9449 section 4.3). */
class DPoPValidationServiceRejectionTest {

    private static final URI URL = URI.create("http://localhost/api/v1/pay-requests/CONS-1");

    private final DPoPNonceRepository nonces = mock(DPoPNonceRepository.class);
    private final DPoPValidationService service = new DPoPValidationService(nonces);

    @Test
    void proofOlderThanFiveMinutesIsRejected() throws Exception {
        when(nonces.saveJtiIfAbsent(anyString(), anyLong())).thenReturn(true);
        String proof = proof(new ECKeyGenerator(Curve.P_256).generate(), "dpop+jwt", Instant.now().minusSeconds(600));

        assertThatThrownBy(() -> service.validateDPoPProof(proof, HttpMethod.GET, URL, "token"))
                .isInstanceOf(DPoPValidationException.class).hasMessageContaining("too old");
    }

    @Test
    void proofFromTheFutureIsRejected() throws Exception {
        when(nonces.saveJtiIfAbsent(anyString(), anyLong())).thenReturn(true);
        String proof = proof(new ECKeyGenerator(Curve.P_256).generate(), "dpop+jwt", Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> service.validateDPoPProof(proof, HttpMethod.GET, URL, "token"))
                .isInstanceOf(DPoPValidationException.class).hasMessageContaining("future");
    }

    @Test
    void wrongTypIsRejected() throws Exception {
        String proof = proof(new ECKeyGenerator(Curve.P_256).generate(), "JWT", Instant.now());

        assertThatThrownBy(() -> service.validateDPoPProof(proof, HttpMethod.GET, URL, "token"))
                .isInstanceOf(DPoPValidationException.class).hasMessageContaining("typ");
    }

    @Test
    void symmetricAlgorithmIsRejected() throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(new JOSEObjectType("dpop+jwt")).build(), claims(Instant.now()));
        jwt.sign(new MACSigner("0123456789abcdef0123456789abcdef"));

        assertThatThrownBy(() -> service.validateDPoPProof(jwt.serialize(), HttpMethod.GET, URL, "token"))
                .isInstanceOf(DPoPValidationException.class).hasMessageContaining("algorithm");
    }

    @Test
    void signatureByAnotherKeyThanTheEmbeddedJwkIsRejected() throws Exception {
        ECKey embedded = new ECKeyGenerator(Curve.P_256).generate();
        ECKey signer = new ECKeyGenerator(Curve.P_256).generate();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(new JOSEObjectType("dpop+jwt")).jwk(embedded.toPublicJWK()).build(), claims(Instant.now()));
        jwt.sign(new ECDSASigner(signer));

        assertThatThrownBy(() -> service.validateDPoPProof(jwt.serialize(), HttpMethod.GET, URL, "token"))
                .isInstanceOf(DPoPValidationException.class).hasMessageContaining("signature");
    }

    @Test
    void garbageIsRejected() {
        assertThatThrownBy(() -> service.validateDPoPProof("not-a-jwt", HttpMethod.GET, URL, "token"))
                .isInstanceOf(DPoPValidationException.class).hasMessageContaining("format");
    }

    private static String proof(ECKey key, String typ, Instant iat) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(new JOSEObjectType(typ)).jwk(key.toPublicJWK()).build(), claims(iat));
        jwt.sign(new ECDSASigner(key));
        return jwt.serialize();
    }

    private static JWTClaimsSet claims(Instant iat) {
        return new JWTClaimsSet.Builder().jwtID(UUID.randomUUID().toString()).issueTime(Date.from(iat))
                .claim("htm", "GET").claim("ath", DPoPValidationService.accessTokenHash("token")).claim("htu", URL.toString()).build();
    }
}
