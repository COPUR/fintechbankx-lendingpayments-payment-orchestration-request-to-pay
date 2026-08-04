package com.enterprise.openfinance.requesttopay.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AudienceValidatorTest {

    private static final String EXPECTED_AUDIENCE = "https://api.example.com";
    private AudienceValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AudienceValidator(EXPECTED_AUDIENCE);
    }

    private Jwt createJwt(List<String> audience) {
        Jwt.Builder builder = Jwt.withTokenValue("token-value")
                .header("alg", "none")
                .claim("sub", "user-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));

        if (audience != null && !audience.isEmpty()) {
            builder.audience(audience);
        }
        return builder.build();
    }

    @Test
    void validate_withCorrectAudience_shouldReturnSuccess() {
        Jwt jwt = createJwt(List.of(EXPECTED_AUDIENCE));
        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertFalse(result.hasErrors());
    }

    @Test
    void validate_withMultipleAudiencesIncludingCorrectOne_shouldReturnSuccess() {
        Jwt jwt = createJwt(List.of("some-other-audience", EXPECTED_AUDIENCE));
        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertFalse(result.hasErrors());
    }

    @Test
    void validate_withIncorrectAudience_shouldReturnFailure() {
        Jwt jwt = createJwt(List.of("incorrect-audience"));
        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertTrue(result.hasErrors());
        assertEquals("invalid_token", result.getErrors().iterator().next().getErrorCode());
    }

    @Test
    void validate_withNullAudience_shouldReturnFailure() {
        Jwt jwt = createJwt(null);
        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertTrue(result.hasErrors());
    }

    @Test
    void validate_withEmptyAudienceList_shouldReturnFailure() {
        Jwt jwt = createJwt(List.of());
        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertTrue(result.hasErrors());
    }
}
