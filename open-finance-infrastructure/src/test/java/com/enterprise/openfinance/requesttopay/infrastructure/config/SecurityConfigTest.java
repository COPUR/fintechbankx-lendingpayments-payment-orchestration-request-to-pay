package com.enterprise.openfinance.requesttopay.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void scopesAndKeycloakRealmRolesBecomeAuthorities() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("tpp-user")
                .claim("scope", "payments openid")
                .claim("realm_access", Map.of("roles", List.of("service", "banker")))
                .build();

        assertThat(SecurityConfig.authorities(jwt)).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("SCOPE_payments", "SCOPE_openid", "ROLE_SERVICE", "ROLE_BANKER");
        assertThat(SecurityConfig.authenticationConverter().convert(jwt).getName()).isEqualTo("tpp-user");
    }

    @Test
    void scopeListAndMissingRolesAreHandled() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("x")
                .claim("scope", List.of("payments")).build();

        assertThat(SecurityConfig.authorities(jwt)).extracting(GrantedAuthority::getAuthority)
                .containsExactly("SCOPE_payments");
    }
}
