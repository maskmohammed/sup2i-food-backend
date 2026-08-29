package com.sup2i.food.security.ratelimit;

import com.sup2i.food.common.api.ApiErrorResponse;
import com.sup2i.food.common.api.RequestTrace;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;

public class RateLimitFilter
    extends OncePerRequestFilter {

    private static final String LOGIN_BUCKET =
        "LOGIN";

    private static final String FORGOT_BUCKET =
        "FORGOT_PASSWORD";

    private static final String RESET_BUCKET =
        "RESET_PASSWORD";

    private static final String REFRESH_BUCKET =
        "REFRESH";

    private static final String MFA_BUCKET =
        "MFA";

    private static final int HTTP_TOO_MANY_REQUESTS =
        429;

    private final RateLimitService rateLimitService;
    private final JsonMapper jsonMapper;

    public RateLimitFilter(
        RateLimitService rateLimitService,
        JsonMapper jsonMapper
    ) {
        this.rateLimitService =
            rateLimitService;

        this.jsonMapper =
            jsonMapper;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        String bucketName =
            resolveBucket(request);

        if (bucketName != null) {

            String key =
                clientKey(request);

            RateLimitDecision decision =
                rateLimitService.tryConsume(
                    bucketName,
                    key
                );

            if (!decision.allowed()) {

                reject(
                    request,
                    response,
                    decision.retryAfterSeconds()
                );

                return;
            }
        }

        filterChain.doFilter(
            request,
            response
        );
    }

    private String resolveBucket(
        HttpServletRequest request
    ) {
        if (
            request.getMethod()
                .equals(HttpMethod.POST.toString())
        ) {
            String path =
                request.getRequestURI();

            if (
                path.endsWith(
                    "/api/v1/auth/login"
                )
            ) {
                return LOGIN_BUCKET;
            }

            if (
                path.endsWith(
                    "/api/v1/auth/forgot-password"
                )
            ) {
                return FORGOT_BUCKET;
            }

            if (
                path.endsWith(
                    "/api/v1/auth/reset-password"
                )
            ) {
                return RESET_BUCKET;
            }

            if (
                path.endsWith(
                    "/api/v1/auth/refresh"
                )
            ) {
                return REFRESH_BUCKET;
            }

            if (
                path.contains(
                    "/api/v1/auth/mfa/totp/"
                )
            ) {
                return MFA_BUCKET;
            }
        }

        return null;
    }

    private String clientKey(
        HttpServletRequest request
    ) {
        String remote =
            request.getRemoteAddr();

        if (
            remote == null
            || remote.isBlank()
        ) {
            return "unknown";
        }

        String forwarded =
            request.getHeader(
                "X-Forwarded-For"
            );

        if (forwarded != null) {

            String first =
                forwarded.split(",", 2)[0]
                    .trim();

            if (!first.isBlank()) {
                return first;
            }
        }

        return remote;
    }

    private void reject(
        HttpServletRequest request,
        HttpServletResponse response,
        long retryAfterSeconds
    ) throws IOException {

        if (response.isCommitted()) {
            return;
        }

        String traceId =
            RequestTrace.resolve(request);

        response.setStatus(
            HTTP_TOO_MANY_REQUESTS
        );

        response.setContentType(
            MediaType.APPLICATION_JSON_VALUE
        );

        response.setCharacterEncoding(
            StandardCharsets.UTF_8.name()
        );

        response.setHeader(
            HttpHeaders.RETRY_AFTER,
            String.valueOf(retryAfterSeconds)
        );

        response.setHeader(
            RequestTrace.HEADER,
            traceId
        );

        ApiErrorResponse body =
            new ApiErrorResponse(
                OffsetDateTime.now(),
                HTTP_TOO_MANY_REQUESTS,
                "RATE_LIMITED",
                "Too many requests. Try again later.",
                request.getRequestURI(),
                traceId,
                Map.of(
                    "retryAfterSeconds",
                    retryAfterSeconds
                )
            );

        jsonMapper.writeValue(
            response.getOutputStream(),
            body
        );
    }
}