package com.enterprise.openfinance.requesttopay.infrastructure.config;

import com.enterprise.openfinance.requesttopay.infrastructure.security.AudienceValidator;
import com.enterprise.openfinance.requesttopay.infrastructure.security.DPoPProofFilter;
import com.enterprise.openfinance.requesttopay.infrastructure.security.DPoPRequestVerifier;
import com.enterprise.openfinance.requesttopay.infrastructure.security.DPoPValidationService;
import com.enterprise.openfinance.requesttopay.infrastructure.security.DpopAwareBearerTokenResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stateless OAuth2 resource server for the platform Keycloak realm.
 * Tokens must carry this service in {@code aud}; DPoP-bound tokens and DPoP
 * proofs are verified when present (DPoP is optional for this service).
 * Everything outside /api/v1/pay-requests and the actuator probes is denied.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.expected-audience}")
    private String expectedAudience;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, DPoPValidationService dpopValidationService)
            throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/api/v1/pay-requests/**", "/api/v1/pay-requests").authenticated()
                .anyRequest().denyAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenResolver(new DpopAwareBearerTokenResolver())
                .jwt(jwt -> {
                    jwt.decoder(jwtDecoder());
                    jwt.jwtAuthenticationConverter(authenticationConverter());
                }))
            .addFilterAfter(new DPoPProofFilter(new DPoPRequestVerifier(dpopValidationService)),
                BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Keys come from the JWKS endpoint, fetched lazily on the first token, so
     * the service starts even while Keycloak is unreachable.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer,
                new AudienceValidator(expectedAudience)));
        return jwtDecoder;
    }

    static Converter<Jwt, AbstractAuthenticationToken> authenticationConverter() {
        return jwt -> new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject());
    }

    /** OAuth2 scopes as SCOPE_*, Keycloak realm roles as ROLE_* (upper case). */
    @SuppressWarnings("unchecked")
    static Collection<GrantedAuthority> authorities(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        Object scope = jwt.getClaims().get("scope");
        if (scope instanceof String scopes) {
            for (String s : scopes.trim().split("\\s+")) {
                if (!s.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority("SCOPE_" + s));
                }
            }
        } else if (scope instanceof Collection<?> scopes) {
            scopes.forEach(s -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + s)));
        }
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (realmAccess instanceof Map<?, ?> access && access.get("roles") instanceof Collection<?> roles) {
            ((Collection<Object>) roles).forEach(role -> authorities.add(
                new SimpleGrantedAuthority("ROLE_" + String.valueOf(role).toUpperCase(Locale.ROOT))));
        }
        return authorities;
    }
}
