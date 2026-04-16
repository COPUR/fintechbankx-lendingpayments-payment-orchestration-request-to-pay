package com.enterprise.openfinance.requesttopay.infrastructure.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Service
public class DPoPValidationService {

    private static final Logger log = LoggerFactory.getLogger(DPoPValidationService.class);
    private final DPoPNonceRepository dpopNonceRepository;

    public static final String DPOP_JWT_TYPE = "dpop+jwt";
    private static final Set<JWSAlgorithm> JWS_ALGORITHMS = Set.of(JWSAlgorithm.PS256, JWSAlgorithm.ES256);
    private static final long JTI_TTL_SECONDS = 300; // 5 minutes for JTI replay protection

    public DPoPValidationService(DPoPNonceRepository dpopNonceRepository) {
        this.dpopNonceRepository = dpopNonceRepository;
    }

    public JWK validateDPoPProof(String dpopHeader, HttpMethod httpMethod, URI requestUri) {
        if (dpopHeader == null || dpopHeader.isBlank()) {
            throw new DPoPValidationException("DPoP header is missing or empty");
        }

        SignedJWT signedJWT;
        try {
            signedJWT = SignedJWT.parse(dpopHeader);
        } catch (ParseException e) {
            throw new DPoPValidationException("Invalid DPoP JWT format", e);
        }

        // 1. Verify DPoP JWT header
        JWSHeader header = signedJWT.getHeader();
        if (!DPOP_JWT_TYPE.equals(header.getType().toString())) {
            throw new DPoPValidationException("Invalid DPoP JWT 'typ' header: " + header.getType());
        }
        if (!JWS_ALGORITHMS.contains(header.getAlgorithm())) {
            throw new DPoPValidationException("Unsupported DPoP JWT algorithm: " + header.getAlgorithm());
        }
        if (header.getJWK() == null) {
            throw new DPoPValidationException("DPoP JWT 'jwk' header is missing");
        }

        // 2. Verify DPoP JWT signature
        JWK jwk = header.getJWK();
        try {
            JWKSet jwkSet = new JWKSet(jwk);
            JWSKeySelector<SecurityContext> keySelector = new JWSVerificationKeySelector<>(header.getAlgorithm(), new ImmutableJWKSet<>(jwkSet));

            ConfigurableJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
            jwtProcessor.setJWSKeySelector(keySelector);
            jwtProcessor.process(signedJWT, null); // Signature verification
        } catch (JOSEException | BadJOSEException e) {
            throw new DPoPValidationException("DPoP JWT signature verification failed", e);
        }

        // 3. Verify DPoP JWT claims
        JWTClaimsSet claims;
        try {
            claims = signedJWT.getJWTClaimsSet();
        } catch (ParseException e) {
            throw new DPoPValidationException("Failed to parse DPoP JWT claims", e);
        }

        // Check 'jti' (JWT ID) for replay protection
        String jti = claims.getJWTID();
        if (jti == null || jti.isBlank()) {
            throw new DPoPValidationException("DPoP JWT 'jti' claim is missing or empty");
        }
        if (!dpopNonceRepository.saveJtiIfAbsent(jti, JTI_TTL_SECONDS)) {
            throw new DPoPValidationException("DPoP JWT 'jti' replay detected");
        }

        // Check 'htm' (HTTP method)
        String htm = claims.getStringClaim("htm");
        if (htm == null || !htm.equalsIgnoreCase(httpMethod.name())) {
            throw new DPoPValidationException("DPoP JWT 'htm' claim mismatch. Expected " + httpMethod.name() + ", got " + htm);
        }

        // Check 'htu' (HTTP URI)
        String htu = claims.getStringClaim("htu");
        if (htu == null || !htu.equals(requestUri.toString())) {
            throw new DPoPValidationException("DPoP JWT 'htu' claim mismatch. Expected " + requestUri.toString() + ", got " + htu);
        }

        // Check 'iat' (Issued At)
        Date iat = claims.getIssueTime();
        if (iat == null) {
            throw new DPoPValidationException("DPoP JWT 'iat' claim is missing");
        }
        // Ensure 'iat' is not in the future (allowing for some clock skew, e.g., 5 minutes)
        if (iat.after(Date.from(Instant.now().plusSeconds(300)))) {
            throw new DPoPValidationException("DPoP JWT 'iat' claim is in the future");
        }
        // Ensure 'iat' is not too old (e.g., within the last 5 minutes)
        if (iat.before(Date.from(Instant.now().minusSeconds(JTI_TTL_SECONDS)))) {
            throw new DPoPValidationException("DPoP JWT 'iat' claim is too old");
        }

        log.debug("DPoP proof validated successfully for jti: {}", jti);
        return jwk;
    }
}
