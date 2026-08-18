package com.sup2i.food.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http
    ) throws Exception {

        http
            .csrf(csrf -> csrf.disable())

            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            .authorizeHttpRequests(authorize ->
                authorize
                    .requestMatchers(
                        "/auth/login",
                        "/auth/refresh",
                        "/auth/logout",
                        "/actuator/health"
                    )
                    .permitAll()

                    .anyRequest()
                    .authenticated()
            )

            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwt ->
                    jwt.jwtAuthenticationConverter(
                        jwtAuthenticationConverter()
                    )
                )
            );

        return http.build();
    }

    private Converter<Jwt, AbstractAuthenticationToken>
    jwtAuthenticationConverter() {

        return jwt -> {
            Collection<GrantedAuthority> authorities =
                new ArrayList<>();

            List<String> roles =
                jwt.getClaimAsStringList("roles");

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
                jwt.getClaimAsStringList("permissions");

            if (permissions != null) {
                permissions.forEach(permission ->
                    authorities.add(
                        new SimpleGrantedAuthority(permission)
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