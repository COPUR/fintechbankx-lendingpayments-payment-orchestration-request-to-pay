package com.enterprise.openfinance.requesttopay.infrastructure.rest;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Which TPP is calling. With an access token the TPP is the OAuth2 client
 * that obtained it ({@code azp}, else {@code client_id}); the
 * x-fapi-financial-id header may repeat it but never override it. Only
 * without an authenticated token (unit tests, never in the running service
 * where every /open-finance path is authenticated) is the header used as is.
 */
final class TppIdentity {

    static final String UNKNOWN_TPP = "UNKNOWN_TPP";

    private TppIdentity() {
    }

    static String resolve(String financialId) {
        String header = financialId == null || financialId.isBlank() ? null : financialId.trim();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String client = firstNonBlank(jwt.getToken().getClaimAsString("azp"),
                    jwt.getToken().getClaimAsString("client_id"));
            if (client == null) {
                throw new AccessDeniedException("Access token does not identify the calling client");
            }
            if (header != null && !header.equals(client)) {
                throw new AccessDeniedException("x-fapi-financial-id does not match the authenticated client");
            }
            return client;
        }
        return header == null ? UNKNOWN_TPP : header;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }
}
