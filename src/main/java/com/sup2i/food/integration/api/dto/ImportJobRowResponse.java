package com.sup2i.food.integration.api.dto;

import com.sup2i.food.integration.domain.ImportRowStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ImportJobRowResponse(
    UUID id,
    UUID importJobId,
    int rowNumber,
    String rawDataJson,
    ImportRowStatus status,
    UUID localEntityId,
    String errorCode,
    String errorMessage,
    OffsetDateTime processedAt,
    boolean replayed
) {
}