package com.sup2i.food.common.api;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public final class RequestTrace {

    public static final String HEADER =
        "X-Request-ID";

    public static final String ATTRIBUTE =
        RequestTrace.class.getName()
            + ".traceId";

    private RequestTrace() {
    }

    public static String resolve(
        HttpServletRequest request
    ) {
        Object value =
            request.getAttribute(
                ATTRIBUTE
            );

        if (value instanceof String traceId
            && !traceId.isBlank()) {

            return traceId;
        }

        String generated =
            UUID.randomUUID().toString();

        request.setAttribute(
            ATTRIBUTE,
            generated
        );

        return generated;
    }
}