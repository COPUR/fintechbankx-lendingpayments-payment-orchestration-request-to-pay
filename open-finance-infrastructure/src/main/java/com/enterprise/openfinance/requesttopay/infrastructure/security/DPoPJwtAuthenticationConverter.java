package com.enterprise.openfinance.requesttopay.infrastructure.security;

import com.nimbusds.jose.jwk.JWK;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.Optional;

public class DPoPJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtAuthenticationConverter delegate;

    public DPoPJwtAuthenticationConverter(JwtAuthenticationConverter delegate) {
        this.delegate = delegate;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        HttpServletRequest request = Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest)
                .orElseThrow(() -> new DPoPValidationException("No HttpServletRequest found for DPoP validation"));

        JWK dpopJwk = (JWK) request.getAttribute(DPoPSecurityAspect.DPOP_JWK_REQUEST_ATTRIBUTE);
        if (dpopJwk == null) {
            throw new DPoPValidationException("DPoP JWK not found in request attributes. Ensure @DPoPSecured is used.");
        }

        Map<String, Object> cnf = jwt.getClaimAsMap("cnf");
        if (cnf == null || !cnf.containsKey("jkt")) {
            throw new DPoPValidationException("Access token 'cnf' claim with 'jkt' is missing");
        }

        String jktFromToken = (String) cnf.get("jkt");
        String jktFromDpop;
        try {
            jktFromDpop = dpopJwk.computeThumbprint("SHA-256").toString();
        } catch (Exception e) {
            throw new DPoPValidationException("Failed to compute DPoP JWK thumbprint", e);
        }

        if (!jktFromToken.equals(jktFromDpop)) {
            throw new DPoPValidationException("DPoP JWK thumbprint does not match access token 'cnf' claim");
        }

        // If validation passes, delegate to the standard converter for authorities and token creation
        return delegate.convert(jwt);
    }
}
