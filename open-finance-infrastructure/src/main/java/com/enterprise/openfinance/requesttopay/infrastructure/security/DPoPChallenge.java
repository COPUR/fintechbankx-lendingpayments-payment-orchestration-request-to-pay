package com.enterprise.openfinance.requesttopay.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * RFC 9449 section 7.1 challenges. Every 401 from this service names the DPoP
 * scheme, so a TPP that sent no token, an invalid token or a Bearer token
 * learns that DPoP is required.
 */
public final class DPoPChallenge implements AuthenticationEntryPoint {

    static final String ALGS = "algs=\"ES256 PS256\"";
    public static final String MISSING_TOKEN = "DPoP " + ALGS;
    public static final String INVALID_TOKEN = "DPoP error=\"invalid_token\", " + ALGS;
    public static final String INVALID_PROOF = "DPoP error=\"invalid_dpop_proof\", " + ALGS;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        boolean tokenPresented = request.getHeader(HttpHeaders.AUTHORIZATION) != null;
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, tokenPresented ? INVALID_TOKEN : MISSING_TOKEN);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
