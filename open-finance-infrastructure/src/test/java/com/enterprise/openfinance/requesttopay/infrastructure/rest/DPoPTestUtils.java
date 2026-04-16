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

    public static String createDPoPProof(ECKey key, HttpMethod method, String url) throws Exception {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(new JOSEObjectType(com.enterprise.openfinance.requesttopay.infrastructure.security.DPoPValidationService.DPOP_JWT_TYPE))
                .jwk(key.toPublicJWK())
                .build();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(Instant.now()))
                .claim("htm", method.name())
                .claim("htu", url)
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claims);
        signedJWT.sign(new ECDSASigner(key));
        return signedJWT.serialize();
    }

    public static Jwt createJwtWithCnf(ECKey key) throws Exception {
        String jkt = key.computeThumbprint("SHA-256").toString();
        Map<String, Object> cnf = Collections.singletonMap("jkt", jkt);

        return Jwt.withTokenValue("token")
                .header("alg", "ES256")
                .claim("sub", "user")
                .claim("scope", "payments")
                .claim("cnf", cnf)
                .build();
    }
}
