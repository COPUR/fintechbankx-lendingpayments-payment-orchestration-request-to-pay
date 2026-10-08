package com.enterprise.openfinance.requesttopay.infrastructure.config;

import com.enterprise.openfinance.requesttopay.infrastructure.security.AudienceValidator;
import com.enterprise.openfinance.requesttopay.infrastructure.security.DPoPChallenge;
import com.enterprise.openfinance.requesttopay.infrastructure.security.DPoPProofFilter;
import com.enterprise.openfinance.requesttopay.infrastructure.security.DPoPRequestVerifier;
import com.enterprise.openfinance.requesttopay.infrastructure.security.DPoPValidationService;
import com.enterprise.openfinance.requesttopay.infrastructure.security.DpopAwareBearerTokenResolver;
import com.enterprise.openfinance.requesttopay.infrastructure.security.TppClientPolicy;
import org.springframework.security.authorization.AuthorizationDecision;
import jakarta.servlet.DispatcherType;
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
 * Every endpoint is TPP-facing (/open-finance/v1, monolith paths) and needs a
 * DPoP-bound token: {@code Authorization: DPoP}, a valid proof with a unique jti,
 * {@code cnf.jkt} matching the proof key, this service in {@code aud}, the
 * payments scope, and a TPP as the calling client ({@link TppClientPolicy}).
 * There is no internal /api/v1 surface: no caller exists.
 * Everything outside those paths and the actuator probes is denied.
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

    @Value("${requesttopay.security.tpp.required-scope:payments}")
    private String requiredScope;

    @Value("${requesttopay.security.tpp.denied-client-prefixes:svc-}")
    private List<String> deniedClientPrefixes;

    @Value("${requesttopay.security.tpp.denied-client-ids:fintechbankx-web,fintechbankx-mobile}")
    private List<String> deniedClientIds;

    @Value("${requesttopay.security.tpp.client-type-claim:fbx_client_type}")
    private String clientTypeClaim;

    @Value("${requesttopay.security.tpp.client-type:open-finance-tpp}")
    private String tppClientType;

    /** TPP clients admitted without the client-type claim (the gateway's rtp-cutover-cohort); empty admits none. */
    @Value("${requesttopay.security.tpp.allowed-clients:}")
    private List<String> allowedClients;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, DPoPValidationService dpopValidationService)
            throws Exception {
        TppClientPolicy tppPolicy = new TppClientPolicy(requiredScope, deniedClientPrefixes, deniedClientIds,
                clientTypeClaim, tppClientType, allowedClients);
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                // Container error dispatches (sendError -> /error) carry the status of a request that
                // already passed this chain; denying them would turn a 404/405 into 401/403.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/open-finance/v1/par", "/open-finance/v1/payment-consents/**")
                    .access((authentication, context) -> new AuthorizationDecision(tppPolicy.allows(authentication.get())))
                .anyRequest().denyAll()
            )
            .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(new DPoPChallenge()))
            .oauth2ResourceServer(oauth2 -> oauth2
                .authenticationEntryPoint(new DPoPChallenge())
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
