package com.enterprise.openfinance.requesttopay.infrastructure.security;

import com.nimbusds.jose.jwk.JWK;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * Optional DPoP (RFC 9449) for the request-to-pay API. DPoP is not mandatory
 * for this payments service (platform contract addendum 2026-10-08), but when
 * it is used it is verified completely:
 * <ul>
 *   <li>a DPoP proof header is validated (signature with the embedded JWK,
 *       typ, alg, htm, htu, iat window, jti replay) and must match the
 *       access token's {@code cnf.jkt};</li>
 *   <li>a DPoP-bound access token ({@code cnf.jkt}) without a proof is refused;</li>
 *   <li>a plain bearer token without a proof is accepted.</li>
 * </ul>
 * The result is stored on the request, so the filter and the
 * {@code @DPoPSecured} aspect verify each request once.
 */
public class DPoPRequestVerifier {

    public static final String DPOP_JWK_REQUEST_ATTRIBUTE = "dpop_jwk";
    static final String VERIFIED_ATTRIBUTE = DPoPRequestVerifier.class.getName() + ".verified";

    private final DPoPValidationService validationService;

    public DPoPRequestVerifier(DPoPValidationService validationService) {
        this.validationService = validationService;
    }

    /**
     * @throws DPoPValidationException when the proof or the token binding is invalid
     */
    public void verify(HttpServletRequest request, Authentication authentication) {
        if (request.getAttribute(VERIFIED_ATTRIBUTE) != null) {
            return;
        }
        String proof = request.getHeader("DPoP");
        String boundThumbprint = boundThumbprint(authentication);

        if (proof == null || proof.isBlank()) {
            if (boundThumbprint != null) {
                throw new DPoPValidationException("DPoP proof is required for a DPoP-bound access token");
            }
            request.setAttribute(VERIFIED_ATTRIBUTE, Boolean.TRUE);
            return;
        }

        JWK proofKey = validationService.validateDPoPProof(proof, HttpMethod.valueOf(request.getMethod()),
                requestUri(request));
        if (authentication instanceof JwtAuthenticationToken) {
            if (boundThumbprint == null) {
                throw new DPoPValidationException("Access token 'cnf' claim with 'jkt' is missing");
            }
            if (!boundThumbprint.equals(thumbprint(proofKey))) {
                throw new DPoPValidationException("DPoP JWK thumbprint does not match access token 'cnf' claim");
            }
        }
        request.setAttribute(DPOP_JWK_REQUEST_ATTRIBUTE, proofKey);
        request.setAttribute(VERIFIED_ATTRIBUTE, Boolean.TRUE);
    }

    private static String boundThumbprint(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            return null;
        }
        Object cnf = jwt.getToken().getClaims().get("cnf");
        if (cnf instanceof Map<?, ?> map && map.get("jkt") instanceof String jkt && !jkt.isBlank()) {
            return jkt;
        }
        return null;
    }

    private static String thumbprint(JWK key) {
        try {
            return key.computeThumbprint("SHA-256").toString();
        } catch (Exception e) {
            throw new DPoPValidationException("Failed to compute DPoP JWK thumbprint", e);
        }
    }

    /** htu is compared without query and fragment (RFC 9449 section 4.3). */
    private static URI requestUri(HttpServletRequest request) {
        try {
            return new URI(request.getRequestURL().toString());
        } catch (URISyntaxException e) {
            throw new DPoPValidationException("Invalid request URI", e);
        }
    }
}
