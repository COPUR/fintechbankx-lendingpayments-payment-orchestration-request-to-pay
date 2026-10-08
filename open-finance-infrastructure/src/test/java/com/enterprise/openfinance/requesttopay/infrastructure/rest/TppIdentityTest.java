package com.enterprise.openfinance.requesttopay.infrastructure.rest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TppIdentityTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authorisedPartyOfTheTokenIsTheTpp() {
        authenticate(Jwt.withTokenValue("t").header("alg", "RS256").claim("azp", "TPP-001").build());

        assertThat(TppIdentity.resolve(null)).isEqualTo("TPP-001");
        assertThat(TppIdentity.resolve(" TPP-001 ")).isEqualTo("TPP-001");
    }

    @Test
    void clientIdClaimIsTheFallback() {
        authenticate(Jwt.withTokenValue("t").header("alg", "RS256").claim("client_id", "TPP-009").build());

        assertThat(TppIdentity.resolve("")).isEqualTo("TPP-009");
    }

    @Test
    void headerCannotImpersonateAnotherTpp() {
        authenticate(Jwt.withTokenValue("t").header("alg", "RS256").claim("azp", "TPP-001").build());

        assertThatThrownBy(() -> TppIdentity.resolve("TPP-002")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void tokenWithoutClientIsRefused() {
        authenticate(Jwt.withTokenValue("t").header("alg", "RS256").claim("sub", "x").build());

        assertThatThrownBy(() -> TppIdentity.resolve("TPP-001")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void withoutAuthenticationTheHeaderOrUnknownIsUsed() {
        assertThat(TppIdentity.resolve("TPP-003")).isEqualTo("TPP-003");
        assertThat(TppIdentity.resolve(null)).isEqualTo(TppIdentity.UNKNOWN_TPP);
    }

    private static void authenticate(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
