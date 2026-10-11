package com.enterprise.openfinance.requesttopay.infrastructure.rest;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

public class DPoPTestUtils {

    /** A proof for the test access token "token" (the value createJwtWithCnf issues). */
    public static String createDPoPProof(ECKey key, HttpMethod method, String url) throws Exception {
        return createDPoPProof(key, method, url, "token");
    }

    public static String createDPoPProof(ECKey key, HttpMethod method, String url, String accessToken)
            throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(new JOSEObjectType(com.enterprise.openfinance.requesttopay.infrastructure.security.DPoPValidationService.DPOP_JWT_TYPE))
                .jwk(key.toPublicJWK())
                .build();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(Instant.now()))
                .claim("htm", method.name())
                .claim("htu", url)
                .claim("ath", com.enterprise.openfinance.requesttopay.infrastructure.security.DPoPValidationService
                        .accessTokenHash(accessToken))
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(new ECDSASigner(key));
        return signedJWT.serialize();
    }

    public static Jwt createJwtWithCnf(ECKey key) throws Exception {
        return createJwtWithCnf(key, "TPP-001");
    }

    /** The claim the realm's default client scope puts on every open-finance TPP client's token. */
    public static final Map<String, Object> TPP_CLIENT_TYPE = Map.of("fbx_client_type", "open-finance-tpp");

    /**
     * A DPoP-bound TPP token for {@code azp} (scope payments, fbx_client_type=open-finance-tpp);
     * {@code azp == null} gives a token without client identity.
     */
    public static Jwt createJwtWithCnf(ECKey key, String azp) throws Exception {
        return createJwtWithCnf(key, azp, "payments", TPP_CLIENT_TYPE);
    }

    /** A DPoP-bound token for {@code azp} with the given scope and extra claims. */
    public static Jwt createJwtWithCnf(ECKey key, String azp, String scope, Map<String, Object> claims)
            throws Exception {
        String jkt = key.computeThumbprint("SHA-256").toString();
        Map<String, Object> cnf = Collections.singletonMap("jkt", jkt);

        Jwt.Builder token = Jwt.withTokenValue("token")
                .header("alg", "ES256")
                .claim("sub", "user")
                .claim("cnf", cnf);
        if (scope != null) {
            token.claim("scope", scope);
        }
        if (azp != null) {
            token.claim("azp", azp);
        }
        claims.forEach(token::claim);
        return token.build();
    }
}
