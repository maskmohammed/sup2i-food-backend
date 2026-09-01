package com.sup2i.food.integration.api.dto;

import com.sup2i.food.integration.domain.IntegrationInboxStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IntegrationInboxEventResponse(
    UUID id,
    UUID connectorId,
    String externalEventId,
    String eventType,
    String payloadJson,
    IntegrationInboxStatus status,
    OffsetDateTime receivedAt,
    OffsetDateTime processedAt,
    int retryCount,
    String lastError,
    boolean replayed
) {
}