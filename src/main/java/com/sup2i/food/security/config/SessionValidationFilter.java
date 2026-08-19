package com.sup2i.food.security.config;

import com.sup2i.food.security.service.SessionValidationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class SessionValidationFilter
    extends OncePerRequestFilter {

    private final SessionValidationService sessionValidationService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    public SessionValidationFilter(
        SessionValidationService sessionValidationService,
        RestAuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.sessionValidationService =
            sessionValidationService;

        this.authenticationEntryPoint =
            authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        Authentication authentication =
            SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (!(authentication
            instanceof JwtAuthenticationToken jwtAuthentication)) {

            filterChain.doFilter(
                request,
                response
            );

            return;
        }

        String subject =
            jwtAuthentication
                .getToken()
                .getSubject();

        String sessionClaim =
            jwtAuthentication
                .getToken()
                .getClaimAsString("sid");

        UUID userId;
        UUID sessionId;

        try {

            if (subject == null
                || sessionClaim == null) {

                throw new IllegalArgumentException();
            }

            userId =
                UUID.fromString(subject);

            sessionId =
                UUID.fromString(sessionClaim);

        } catch (
            IllegalArgumentException exception
        ) {

            reject(
                request,
                response,
                "Invalid JWT session."
            );

            return;
        }

        boolean active =
            sessionValidationService
                .isSessionActive(
                    sessionId,
                    userId
                );

        if (!active) {

            reject(
                request,
                response,
                "Session has been revoked."
            );

            return;
        }

        filterChain.doFilter(
            request,
            response
        );
    }

    private void reject(
        HttpServletRequest request,
        HttpServletResponse response,
        String message
    ) throws IOException, ServletException {

        SecurityContextHolder.clearContext();

        authenticationEntryPoint.commence(
            request,
            response,
            new BadCredentialsException(message)
        );
    }
}