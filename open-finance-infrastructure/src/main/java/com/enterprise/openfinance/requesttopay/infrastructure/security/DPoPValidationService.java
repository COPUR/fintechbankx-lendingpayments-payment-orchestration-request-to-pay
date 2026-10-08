package com.enterprise.openfinance.requesttopay.infrastructure.security;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.text.ParseException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Set;

/**
 * Validates a DPoP proof (RFC 9449 section 4.3) for a request that carries an
 * access token: typ, alg, signature with the embedded JWK, htm, htu, an iat
 * no older than {@link #MAX_AGE} and no more than {@link #FUTURE_SKEW} ahead,
 * and ath = base64url(SHA-256(access token)). The jti is recorded only after
 * every other check passed, and it is remembered until the proof can no
 * longer be accepted: max(now, iat) + max age + skew. Messages never echo the
 * request URL or claim values (they may carry ids).
 */
@Service
public class DPoPValidationService {

    private static final Logger log = LoggerFactory.getLogger(DPoPValidationService.class);

    public static final String DPOP_JWT_TYPE = "dpop+jwt";
    static final Duration MAX_AGE = Duration.ofSeconds(300);
    static final Duration FUTURE_SKEW = Duration.ofSeconds(60);
    private static final Set<JWSAlgorithm> JWS_ALGORITHMS = Set.of(JWSAlgorithm.PS256, JWSAlgorithm.ES256);

    private final DPoPNonceRepository dpopNonceRepository;
    private final Clock clock;

    @Autowired
    public DPoPValidationService(DPoPNonceRepository dpopNonceRepository, Clock clock) {
        this.dpopNonceRepository = dpopNonceRepository;
        this.clock = clock;
    }

    public DPoPValidationService(DPoPNonceRepository dpopNonceRepository) {
        this(dpopNonceRepository, Clock.systemUTC());
    }

    /**
     * @param accessToken the access token value the proof accompanies (its hash must be the proof's ath)
     */
    public JWK validateDPoPProof(String dpopHeader, HttpMethod httpMethod, URI requestUri, String accessToken) {
        if (dpopHeader == null || dpopHeader.isBlank()) {
            throw new DPoPValidationException("DPoP header is missing or empty");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new DPoPValidationException("DPoP proof must accompany an access token");
        }

        SignedJWT signedJWT;
        try {
            signedJWT = SignedJWT.parse(dpopHeader);
        } catch (ParseException e) {
            throw new DPoPValidationException("Invalid DPoP JWT format", e);
        }

        JWSHeader header = signedJWT.getHeader();
        if (header.getType() == null || !DPOP_JWT_TYPE.equals(header.getType().toString())) {
            throw new DPoPValidationException("Invalid DPoP JWT 'typ' header");
        }
        if (!JWS_ALGORITHMS.contains(header.getAlgorithm())) {
            throw new DPoPValidationException("Unsupported DPoP JWT algorithm");
        }
        if (header.getJWK() == null) {
            throw new DPoPValidationException("DPoP JWT 'jwk' header is missing");
        }

        JWK jwk = header.getJWK();
        try {
            JWSVerifier verifier;
            if (jwk instanceof ECKey ecKey) {
                verifier = new ECDSAVerifier(ecKey);
            } else if (jwk instanceof RSAKey rsaKey) {
                verifier = new RSASSAVerifier(rsaKey);
            } else {
                throw new DPoPValidationException("Unsupported DPoP JWT JWK type");
            }
            if (!signedJWT.verify(verifier)) {
                throw new DPoPValidationException("DPoP JWT signature verification failed");
            }
        } catch (JOSEException e) {
            throw new DPoPValidationException("DPoP JWT signature verification failed", e);
        }

        JWTClaimsSet claims;
        try {
            claims = signedJWT.getJWTClaimsSet();
        } catch (ParseException e) {
            throw new DPoPValidationException("Failed to parse DPoP JWT claims", e);
        }

        String jti = claims.getJWTID();
        if (jti == null || jti.isBlank()) {
            throw new DPoPValidationException("DPoP JWT 'jti' claim is missing or empty");
        }
        String htm;
        String htu;
        String ath;
        try {
            htm = claims.getStringClaim("htm");
            htu = claims.getStringClaim("htu");
            ath = claims.getStringClaim("ath");
        } catch (ParseException e) {
            throw new DPoPValidationException("Failed to parse DPoP claims", e);
        }
        if (htm == null || !htm.equalsIgnoreCase(httpMethod.name())) {
            throw new DPoPValidationException("DPoP JWT 'htm' claim does not match the request method");
        }
        if (htu == null || !htu.equals(requestUri.toString())) {
            throw new DPoPValidationException("DPoP JWT 'htu' claim does not match the request URL");
        }

        Instant now = clock.instant();
        Date issued = claims.getIssueTime();
        if (issued == null) {
            throw new DPoPValidationException("DPoP JWT 'iat' claim is missing");
        }
        Instant iat = issued.toInstant();
        if (iat.isAfter(now.plus(FUTURE_SKEW))) {
            throw new DPoPValidationException("DPoP JWT 'iat' claim is in the future");
        }
        if (iat.isBefore(now.minus(MAX_AGE))) {
            throw new DPoPValidationException("DPoP JWT 'iat' claim is too old");
        }

        if (ath == null || ath.isBlank()) {
            throw new DPoPValidationException("DPoP JWT 'ath' claim is missing");
        }
        if (!MessageDigest.isEqual(ath.getBytes(StandardCharsets.US_ASCII),
                accessTokenHash(accessToken).getBytes(StandardCharsets.US_ASCII))) {
            throw new DPoPValidationException("DPoP JWT 'ath' claim does not match the access token");
        }

        // Remember the jti until no clock in the accepted window would take this proof again.
        Instant acceptedUntil = (iat.isAfter(now) ? iat : now).plus(MAX_AGE).plus(FUTURE_SKEW);
        long ttlSeconds = Duration.between(now, acceptedUntil).toSeconds();
        if (!dpopNonceRepository.saveJtiIfAbsent(jti, ttlSeconds)) {
            throw new DPoPValidationException("DPoP JWT 'jti' replay detected");
        }

        log.debug("DPoP proof validated");
        return jwk;
    }

    /** base64url (no padding) of SHA-256 over the ASCII access token (RFC 9449 section 4.2). */
    public static String accessTokenHash(String accessToken) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(accessToken.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
