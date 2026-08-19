package com.sup2i.food.security.config;

import com.sup2i.food.common.api.ApiErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Component
public class RestAuthenticationEntryPoint
    implements AuthenticationEntryPoint {

    private final JsonMapper jsonMapper;

    public RestAuthenticationEntryPoint(
        JsonMapper jsonMapper
    ) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException exception
    ) throws IOException, ServletException {

        if (response.isCommitted()) {
            return;
        }

        response.setStatus(
            HttpServletResponse.SC_UNAUTHORIZED
        );

        response.setContentType(
            MediaType.APPLICATION_JSON_VALUE
        );

        response.setCharacterEncoding(
            StandardCharsets.UTF_8.name()
        );

        response.setHeader(
            HttpHeaders.WWW_AUTHENTICATE,
            "Bearer"
        );

        ApiErrorResponse body =
            new ApiErrorResponse(
                OffsetDateTime.now(),
                HttpServletResponse.SC_UNAUTHORIZED,
                "UNAUTHORIZED",
                "Authentication required or session is no longer valid.",
                request.getRequestURI(),
                UUID.randomUUID().toString(),
                Map.of()
            );

        jsonMapper.writeValue(
            response.getOutputStream(),
            body
        );
    }
}