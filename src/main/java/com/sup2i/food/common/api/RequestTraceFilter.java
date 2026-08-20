package com.sup2i.food.common.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter
    extends OncePerRequestFilter {

    private static final Pattern SAFE_ID =
        Pattern.compile(
            "^[A-Za-z0-9][A-Za-z0-9._-]{7,127}$"
        );

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        String requested =
            request.getHeader(
                RequestTrace.HEADER
            );

        String traceId =
            isSafe(requested)
                ? requested
                : UUID.randomUUID()
                    .toString();

        request.setAttribute(
            RequestTrace.ATTRIBUTE,
            traceId
        );

        response.setHeader(
            RequestTrace.HEADER,
            traceId
        );

        MDC.put(
            "traceId",
            traceId
        );

        try {
            filterChain.doFilter(
                request,
                response
            );
        }
        finally {
            MDC.remove(
                "traceId"
            );
        }
    }

    private boolean isSafe(
        String value
    ) {
        return value != null
            && SAFE_ID
                .matcher(value)
                .matches();
    }
}