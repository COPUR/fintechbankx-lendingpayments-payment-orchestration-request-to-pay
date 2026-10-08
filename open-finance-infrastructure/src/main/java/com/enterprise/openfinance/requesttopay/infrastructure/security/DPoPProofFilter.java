package com.enterprise.openfinance.requesttopay.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs after bearer-token authentication for /api/** and applies
 * {@link DPoPRequestVerifier}. Failures answer 401 with a DPoP challenge.
 */
public class DPoPProofFilter extends OncePerRequestFilter {

    private final DPoPRequestVerifier verifier;

    public DPoPProofFilter(DPoPRequestVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            verifier.verify(request, SecurityContextHolder.getContext().getAuthentication());
        } catch (DPoPValidationException e) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "DPoP error=\"invalid_dpop_proof\"");
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":\"DPOP_VALIDATION_FAILED\",\"message\":\"Invalid DPoP proof\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
