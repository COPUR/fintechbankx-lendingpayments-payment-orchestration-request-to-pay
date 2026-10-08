package com.enterprise.openfinance.requesttopay.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;

/**
 * Who may call the TPP paths, on top of aud (checked by the decoder) and the
 * DPoP binding (checked by the proof filter):
 * <ul>
 *   <li>the token carries the payments scope ({@code requiredScope});</li>
 *   <li>the calling client ({@code azp}, else {@code client_id}) is never a
 *   platform service client ({@code svc-*}) or a first-party channel client
 *   (fintechbankx-web, fintechbankx-mobile). Keycloak adds this service's
 *   audience to service and channel clients too, so aud alone does not say
 *   "TPP";</li>
 *   <li>the client is positively known to be a TPP (allow-list, fail closed):
 *   when the token carries the client-type claim, it must name a TPP
 *   ({@code open-finance-tpp}); when it does not, the client must be in
 *   {@code allowedClients}, the same TPP cohort the gateway routes here
 *   ({@code rtp-cutover-cohort}). An empty list admits no client without the
 *   claim. The realm tags TPP clients fbx.client-type=open-finance-tpp but does
 *   not put it in tokens yet; once a mapper does, the claim decides.</li>
 * </ul>
 */
public final class TppClientPolicy {

    private final String requiredScope;
    private final List<String> deniedClientPrefixes;
    private final List<String> deniedClientIds;
    private final String clientTypeClaim;
    private final String tppClientType;
    private final List<String> allowedClients;

    public TppClientPolicy(String requiredScope, Collection<String> deniedClientPrefixes,
                           Collection<String> deniedClientIds, String clientTypeClaim, String tppClientType,
                           Collection<String> allowedClients) {
        if (requiredScope == null || requiredScope.isBlank()) {
            throw new IllegalArgumentException("requesttopay.security.tpp.required-scope must be set");
        }
        this.requiredScope = requiredScope.trim();
        this.deniedClientPrefixes = clean(deniedClientPrefixes);
        this.deniedClientIds = clean(deniedClientIds);
        this.clientTypeClaim = clientTypeClaim;
        this.tppClientType = tppClientType;
        this.allowedClients = clean(allowedClients);
    }

    public boolean allows(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !authentication.isAuthenticated()) {
            return false;
        }
        boolean hasScope = jwt.getAuthorities().stream()
                .anyMatch(a -> ("SCOPE_" + requiredScope).equals(a.getAuthority()));
        return hasScope && isTppClient(jwt.getToken());
    }

    private boolean isTppClient(Jwt token) {
        String client = clientId(token);
        if (client == null) {
            return false;
        }
        if (deniedClientIds.contains(client) || deniedClientPrefixes.stream().anyMatch(client::startsWith)) {
            return false;
        }
        if (clientTypeClaim != null && !clientTypeClaim.isBlank() && token.hasClaim(clientTypeClaim)) {
            return tppClientType != null && tppClientType.equals(token.getClaimAsString(clientTypeClaim));
        }
        return allowedClients.contains(client);
    }

    /** The OAuth2 client that obtained the token: azp, else client_id. */
    public static String clientId(Jwt token) {
        String azp = token.getClaimAsString("azp");
        if (azp != null && !azp.isBlank()) {
            return azp.trim();
        }
        String clientId = token.getClaimAsString("client_id");
        return clientId == null || clientId.isBlank() ? null : clientId.trim();
    }

    private static List<String> clean(Collection<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(v -> v != null && !v.isBlank()).map(String::trim).toList();
    }
}
