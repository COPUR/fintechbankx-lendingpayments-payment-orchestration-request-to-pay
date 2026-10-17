package com.enterprise.openfinance.requesttopay.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * Guarantees DPoP verification on every {@code @DPoPSecured} endpoint, also
 * where the {@link DPoPProofFilter} is not installed. A request the filter
 * already verified is not verified twice (the jti would look like a replay).
 */
@Aspect
@Component
public class DPoPSecurityAspect {

    public static final String DPOP_JWK_REQUEST_ATTRIBUTE = DPoPRequestVerifier.DPOP_JWK_REQUEST_ATTRIBUTE;

    private final DPoPRequestVerifier verifier;

    public DPoPSecurityAspect(DPoPValidationService dpopValidationService) {
        this.verifier = new DPoPRequestVerifier(dpopValidationService);
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
        try {
            verifier.verify(request, SecurityContextHolder.getContext().getAuthentication());
        } catch (DPoPValidationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage(), e);
        }
    }
}
