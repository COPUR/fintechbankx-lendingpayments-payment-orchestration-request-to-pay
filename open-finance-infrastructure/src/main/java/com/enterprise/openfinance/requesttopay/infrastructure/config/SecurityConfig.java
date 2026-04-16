package com.enterprise.openfinance.requesttopay.infrastructure.config;

import com.enterprise.openfinance.requesttopay.infrastructure.security.DPoPJwtAuthenticationConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // Disable CSRF for API endpoints
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/v1/pay-requests/**").authenticated() // All /api/v1/pay-requests endpoints require authentication
                .anyRequest().permitAll() // Other requests are permitted
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                jwt.jwtAuthenticationConverter(dpopJwtAuthenticationConverter());
            }));
        return http.build();
    }

    @Bean
    public DPoPJwtAuthenticationConverter dpopJwtAuthenticationConverter() {
        // Configure the delegate for roles/scopes extraction
        JwtAuthenticationConverter grantedAuthoritiesConverter = new JwtAuthenticationConverter();
        grantedAuthoritiesConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> scopes = jwt.getClaimAsStringList("scope"); // Assuming scopes are in 'scope' claim
            if (scopes == null) {
                return List.of();
            }
            return scopes.stream()
                    .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                    .collect(Collectors.toList());
        });
        return new DPoPJwtAuthenticationConverter(grantedAuthoritiesConverter);
    }
}
