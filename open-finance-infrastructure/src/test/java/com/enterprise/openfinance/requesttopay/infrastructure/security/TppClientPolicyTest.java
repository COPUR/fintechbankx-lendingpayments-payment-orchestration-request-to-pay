package com.enterprise.openfinance.requesttopay.infrastructure.security;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class TppClientPolicyTest {

    private static final List<String> SVC = List.of("svc-");
    private static final List<String> CHANNELS = List.of("fintechbankx-web", "fintechbankx-mobile");

    @Test
    void aListedClientWithoutTheClaimIsAllowed() {
        assertThat(policy(List.of("TPP-001")).allows(token("TPP-001", Map.of()))).isTrue();
    }

    @Test
    void anUnlistedClientWithoutTheClaimIsRefused() {
        assertThat(policy(List.of("TPP-001")).allows(token("TPP-UNKNOWN", Map.of()))).isFalse();
    }

    @Test
    void anEmptyListAdmitsNoClientWithoutTheClaim() {
        assertThat(policy(List.of()).allows(token("TPP-001", Map.of()))).isFalse();
        assertThat(policy(null).allows(token("TPP-001", Map.of()))).isFalse();
    }

    @Test
    void theClaimDecidesWhenPresent() {
        TppClientPolicy policy = policy(List.of("TPP-001"));

        assertThat(policy.allows(token("TPP-NEW", Map.of("fbx_client_type", "open-finance-tpp")))).isTrue();
        assertThat(policy.allows(token("TPP-001", Map.of("fbx_client_type", "first-party-public")))).isFalse();
    }

    @Test
    void serviceAndChannelClientsAreRefusedEvenWhenListedOrTagged() {
        TppClientPolicy policy = policy(List.of("svc-pay-bulk-orchestration", "fintechbankx-web"));
        Map<String, Object> tpp = Map.of("fbx_client_type", "open-finance-tpp");

        assertThat(policy.allows(token("svc-pay-bulk-orchestration", Map.of()))).isFalse();
        assertThat(policy.allows(token("fintechbankx-web", Map.of()))).isFalse();
        assertThat(policy.allows(token("svc-pay-bulk-orchestration", tpp))).isFalse();
        assertThat(policy.allows(token("fintechbankx-web", tpp))).isFalse();
    }

    @Test
    void clientIdIsUsedWhenAzpIsAbsent() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "ES256").claim("sub", "u")
                .claim("client_id", "TPP-001").build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_payments")));

        assertThat(policy(List.of("TPP-001")).allows(auth)).isTrue();
    }

    private static TppClientPolicy policy(List<String> allowed) {
        return new TppClientPolicy("payments", SVC, CHANNELS, "fbx_client_type", "open-finance-tpp", allowed);
    }

    private static JwtAuthenticationToken token(String azp, Map<String, Object> claims) {
        Jwt.Builder jwt = Jwt.withTokenValue("t").header("alg", "ES256").claim("sub", "u").claim("azp", azp);
        claims.forEach(jwt::claim);
        return new JwtAuthenticationToken(jwt.build(), List.of(new SimpleGrantedAuthority("SCOPE_payments")));
    }
}
