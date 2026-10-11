package com.enterprise.openfinance.requesttopay.infrastructure.rest;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.enterprise.openfinance.requesttopay.infrastructure.security.TppClientPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Which TPP is calling: the OAuth2 client that obtained the access token
 * ({@code azp}, else {@code client_id}). The x-fapi-financial-id header may
 * repeat it but never override it. Without an access token nothing is
 * resolved: the call is refused (fail closed), the header is never trusted.
 */
final class TppIdentity {

    private TppIdentity() {
    }

    static String resolve(String financialId) {
        String header = financialId == null || financialId.isBlank() ? null : financialId.trim();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            throw new AccessDeniedException("No access token identifies the calling client");
        }
        String client = TppClientPolicy.clientId(jwt.getToken());
        if (client == null) {
            throw new AccessDeniedException("Access token does not identify the calling client");
        }
        if (header != null && !header.equals(client)) {
            throw new AccessDeniedException("x-fapi-financial-id does not match the authenticated client");
        }
        return client;
    }
}
