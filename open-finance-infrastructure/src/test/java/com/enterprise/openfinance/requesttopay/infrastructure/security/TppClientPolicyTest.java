package com.enterprise.openfinance.requesttopay.infrastructure.security;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class TppClientPolicyTest {

    private static final Map<String, Object> TPP = Map.of("fbx_client_type", "open-finance-tpp");
    private final TppClientPolicy policy = new TppClientPolicy("payments", List.of("svc-"),
            List.of("fintechbankx-web", "fintechbankx-mobile"), "fbx_client_type", "open-finance-tpp");

    @Test
    void theRightClaimWithThePaymentsScopeIsAllowed() {
        assertThat(policy.allows(token("TPP-001", TPP, "payments"))).isTrue();
    }

    @Test
    void anAbsentClaimIsRefused() {
        assertThat(policy.allows(token("TPP-001", Map.of(), "payments"))).isFalse();
    }

    @Test
    void aWrongClaimIsRefused() {
        assertThat(policy.allows(token("TPP-001", Map.of("fbx_client_type", "first-party-public"), "payments")))
                .isFalse();
    }

    @Test
    void theRightClaimWithoutThePaymentsScopeIsRefused() {
        assertThat(policy.allows(token("TPP-001", TPP, "accounts"))).isFalse();
    }

    @Test
    void serviceAndChannelClientsAreRefusedEvenWithTheClaim() {
        assertThat(policy.allows(token("svc-pay-bulk-orchestration", TPP, "payments"))).isFalse();
        assertThat(policy.allows(token("fintechbankx-web", TPP, "payments"))).isFalse();
        assertThat(policy.allows(token("fintechbankx-mobile", TPP, "payments"))).isFalse();
    }

    @Test
    void aTokenWithoutAClientIsRefused() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "ES256").claim("sub", "u")
                .claim("fbx_client_type", "open-finance-tpp").build();

        assertThat(policy.allows(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_payments")))))
                .isFalse();
    }

    @Test
    void clientIdIsUsedWhenAzpIsAbsent() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "ES256").claim("sub", "u")
                .claim("client_id", "svc-pay-bulk-orchestration").claim("fbx_client_type", "open-finance-tpp").build();

        assertThat(policy.allows(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_payments")))))
                .isFalse();
    }

    @Test
    void theClaimSettingsCannotBeBlank() {
        assertThatThrownBy(() -> new TppClientPolicy("payments", List.of(), List.of(), " ", "open-finance-tpp"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("client-type-claim");
        assertThatThrownBy(() -> new TppClientPolicy("payments", List.of(), List.of(), "fbx_client_type", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("client-type");
    }

    private static JwtAuthenticationToken token(String azp, Map<String, Object> claims, String scope) {
        Jwt.Builder jwt = Jwt.withTokenValue("t").header("alg", "ES256").claim("sub", "u").claim("azp", azp);
        claims.forEach(jwt::claim);
        return new JwtAuthenticationToken(jwt.build(), List.of(new SimpleGrantedAuthority("SCOPE_" + scope)));
    }
}
