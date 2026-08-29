package com.sup2i.food.security.config;

import com.sup2i.food.security.ratelimit.RateLimitFilter;
import com.sup2i.food.security.ratelimit.RateLimitService;
import com.sup2i.food.security.service.SessionValidationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain
        securityFilterChain(
            HttpSecurity http,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            SessionValidationService sessionValidationService,
            CorsConfigurationSource corsConfigurationSource,
            RateLimitService rateLimitService,
            JsonMapper jsonMapper,
            Environment environment
        ) throws Exception {

        http
            .csrf(
                csrf ->
                    csrf.disable()
            )

            .headers(headers ->
                headers
                    .frameOptions(frame ->
                        frame.deny()
                    )
                    .contentTypeOptions(
                        Customizer.withDefaults()
                    )
                    .httpStrictTransportSecurity(
                        hsts ->
                            hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(
                                    31536000L
                                )
                    )
                    .contentSecurityPolicy(
                        csp ->
                            csp.policyDirectives(
                                "default-src 'none';"
                                    + " frame-ancestors 'none';"
                                    + " base-uri 'none';"
                                    + " form-action 'none'"
                            )
                    )
                    .referrerPolicy(
                        referrer ->
                            referrer.policy(
                                ReferrerPolicyHeaderWriter
                                    .ReferrerPolicy
                                    .NO_REFERRER
                            )
                    )
            )

            .cors(cors ->
                cors.configurationSource(
                    corsConfigurationSource
                )
            )

            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            .authorizeHttpRequests(authorize ->
                authorize

                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/mfa/totp/setup",
                        "/api/v1/auth/mfa/totp/confirm",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password"
                    )
                    .permitAll()

                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/products/*/reviews"
                    )
                    .permitAll()

                    .requestMatchers(
                        "/actuator/health"
                    )
                    .permitAll()

                    .requestMatchers(
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                    )
                    .permitAll()

                    .anyRequest()
                    .authenticated()
            )

            .exceptionHandling(exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        authenticationEntryPoint
                    )
                    .accessDeniedHandler(
                        accessDeniedHandler
                    )
            )

            .oauth2ResourceServer(oauth2 ->
                oauth2
                    .authenticationEntryPoint(
                        authenticationEntryPoint
                    )
                    .accessDeniedHandler(
                        accessDeniedHandler
                    )
                    .jwt(jwt ->
                        jwt.jwtAuthenticationConverter(
                            jwtAuthenticationConverter()
                        )
                    )
            )

            .addFilterAfter(
                new SessionValidationFilter(
                    sessionValidationService,
                    authenticationEntryPoint
                ),
                BearerTokenAuthenticationFilter.class
            )

            .addFilterBefore(
                new RateLimitFilter(
                    rateLimitService,
                    jsonMapper
                ),
                BearerTokenAuthenticationFilter.class
            );

        if (
            environment.getActiveProfiles().length > 0
            && java.util.Arrays.asList(
                    environment.getActiveProfiles()
                )
                .contains("prod")
        ) {
            http.requiresChannel(channel ->
                channel.anyRequest()
                    .requiresSecure()
            );
        }

        return http.build();
    }

    @Bean
    public CorsConfigurationSource
        corsConfigurationSource(
            SecurityProperties properties
        ) {

        CorsConfiguration configuration =
            new CorsConfiguration();

        SecurityProperties.Cors cors =
            properties.cors();

        if (
            cors != null
            && cors.enabled()
        ) {

            Set<String> configuredOrigins =
                cors.allowedOrigins();

            List<String> origins =
                configuredOrigins == null
                    ? List.of()
                    : configuredOrigins
                        .stream()
                        .filter(origin ->
                            origin != null
                                && !origin.isBlank()
                        )
                        .map(String::trim)
                        .toList();

            configuration.setAllowedOrigins(
                origins
            );

            configuration.setAllowedMethods(
                List.of(
                    "GET",
                    "POST",
                    "PUT",
                    "PATCH",
                    "DELETE",
                    "OPTIONS"
                )
            );

            configuration.setAllowedHeaders(
                List.of(
                    "Authorization",
                    "Content-Type",
                    "X-Request-ID"
                )
            );

            configuration.setExposedHeaders(
                List.of(
                    "X-Request-ID"
                )
            );

            configuration.setAllowCredentials(
                false
            );

            Duration maxAge =
                cors.maxAge();

            configuration.setMaxAge(
                maxAge == null
                    ? 3600L
                    : maxAge.toSeconds()
            );
        }
        else {
            configuration.setAllowedOrigins(
                List.of()
            );
        }

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
            "/**",
            configuration
        );

        return source;
    }

    private Converter<
        Jwt,
        AbstractAuthenticationToken
    > jwtAuthenticationConverter() {

        return jwt -> {

            Collection<GrantedAuthority>
                authorities =
                    new ArrayList<>();

            List<String> roles =
                jwt.getClaimAsStringList(
                    "roles"
                );

            if (roles != null) {
                roles.forEach(role ->
                    authorities.add(
                        new SimpleGrantedAuthority(
                            "ROLE_" + role
                        )
                    )
                );
            }

            List<String> permissions =
                jwt.getClaimAsStringList(
                    "permissions"
                );

            if (permissions != null) {
                permissions.forEach(permission ->
                    authorities.add(
                        new SimpleGrantedAuthority(
                            permission
                        )
                    )
                );
            }

            return new JwtAuthenticationToken(
                jwt,
                authorities,
                jwt.getSubject()
            );
        };
    }
}