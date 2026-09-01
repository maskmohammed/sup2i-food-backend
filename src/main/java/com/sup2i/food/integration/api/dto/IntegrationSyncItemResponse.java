package com.sup2i.food.integration.api.dto;

import com.sup2i.food.integration.domain.IntegrationSyncItemStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IntegrationSyncItemResponse(
    UUID id,
    UUID syncRunId,
    String entityType,
    String externalId,
    UUID localEntityId,
    IntegrationSyncItemStatus status,
    String action,
    String errorCode,
    String errorMessage,
    String payloadJson,
    OffsetDateTime processedAt,
    boolean replayed
) {
}