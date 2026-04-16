package com.enterprise.openfinance.requesttopay.infrastructure.security;

import com.nimbusds.jose.jwk.JWK;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

@Aspect
@Component
public class DPoPSecurityAspect {

    private final DPoPValidationService dpopValidationService;
    public static final String DPOP_JWK_REQUEST_ATTRIBUTE = "dpop_jwk";

    public DPoPSecurityAspect(DPoPValidationService dpopValidationService) {
        this.dpopValidationService = dpopValidationService;
    }

    @Pointcut("@annotation(com.enterprise.openfinance.requesttopay.infrastructure.rest.DPoPSecured)")
    public void dpopSecuredMethods() {
        // Pointcut for methods annotated with @DPoPSecured
    }

    @Before("dpopSecuredMethods()")
    public void validateDPoPProof() {
        HttpServletRequest request = Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest)
                .orElseThrow(() -> new IllegalStateException("No HttpServletRequest found"));

        String dpopHeader = request.getHeader("DPoP");
        HttpMethod httpMethod = HttpMethod.valueOf(request.getMethod());
        URI requestUri;
        try {
            // Reconstruct the full request URI, including query parameters
            requestUri = new URI(request.getRequestURL().toString() +
                                 (request.getQueryString() != null ? "?" + request.getQueryString() : ""));
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request URI", e);
        }

        try {
            JWK dpopJwk = dpopValidationService.validateDPoPProof(dpopHeader, httpMethod, requestUri);
            request.setAttribute(DPOP_JWK_REQUEST_ATTRIBUTE, dpopJwk);
        } catch (DPoPValidationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage(), e);
        }
    }
}
