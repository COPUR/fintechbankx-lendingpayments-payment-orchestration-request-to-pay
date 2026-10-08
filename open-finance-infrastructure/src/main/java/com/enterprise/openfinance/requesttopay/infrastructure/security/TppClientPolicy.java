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
 *   <li>the token carries the client-type claim and it names a TPP
 *   ({@code open-finance-tpp}); a token without the claim is refused (fail
 *   closed). The realm's default client scope fbx-client-type-open-finance-tpp
 *   puts fbx_client_type=open-finance-tpp on every open-finance TPP client's
 *   token.</li>
 * </ul>
 */
public final class TppClientPolicy {

    private final String requiredScope;
    private final List<String> deniedClientPrefixes;
    private final List<String> deniedClientIds;
    private final String clientTypeClaim;
    private final String tppClientType;

    public TppClientPolicy(String requiredScope, Collection<String> deniedClientPrefixes,
                           Collection<String> deniedClientIds, String clientTypeClaim, String tppClientType) {
        if (requiredScope == null || requiredScope.isBlank()) {
            throw new IllegalArgumentException("requesttopay.security.tpp.required-scope must be set");
        }
        this.requiredScope = requiredScope.trim();
        this.deniedClientPrefixes = clean(deniedClientPrefixes);
        this.deniedClientIds = clean(deniedClientIds);
        if (clientTypeClaim == null || clientTypeClaim.isBlank()) {
            throw new IllegalArgumentException("requesttopay.security.tpp.client-type-claim must be set");
        }
        if (tppClientType == null || tppClientType.isBlank()) {
            throw new IllegalArgumentException("requesttopay.security.tpp.client-type must be set");
        }
        this.clientTypeClaim = clientTypeClaim.trim();
        this.tppClientType = tppClientType.trim();
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
        return tppClientType.equals(token.getClaimAsString(clientTypeClaim));
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
