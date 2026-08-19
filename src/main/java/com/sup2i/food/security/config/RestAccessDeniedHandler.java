package com.sup2i.food.security.config;

import com.sup2i.food.common.api.ApiErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Component
public class RestAccessDeniedHandler
    implements AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    public RestAccessDeniedHandler(
        JsonMapper jsonMapper
    ) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void handle(
        HttpServletRequest request,
        HttpServletResponse response,
        AccessDeniedException exception
    ) throws IOException, ServletException {

        if (response.isCommitted()) {
            return;
        }

        response.setStatus(
            HttpServletResponse.SC_FORBIDDEN
        );

        response.setContentType(
            MediaType.APPLICATION_JSON_VALUE
        );

        response.setCharacterEncoding(
            StandardCharsets.UTF_8.name()
        );

        ApiErrorResponse body =
            new ApiErrorResponse(
                OffsetDateTime.now(),
                HttpServletResponse.SC_FORBIDDEN,
                "PERMISSION_DENIED",
                "You do not have permission to access this resource.",
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