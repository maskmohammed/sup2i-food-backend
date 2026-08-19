package com.sup2i.food.common.api;

import java.time.OffsetDateTime;
import java.util.Map;

public record ApiErrorResponse(
    OffsetDateTime timestamp,
    int status,
    String code,
    String message,
    String path,
    String traceId,
    Map<String, Object> details
) {
}