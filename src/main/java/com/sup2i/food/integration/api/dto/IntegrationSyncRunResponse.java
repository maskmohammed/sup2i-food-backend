package com.sup2i.food.integration.api.dto;

import com.sup2i.food.integration.domain.IntegrationSyncRunStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IntegrationSyncRunResponse(
    UUID id,
    UUID connectorId,
    String syncType,
    IntegrationSyncRunStatus status,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt,
    int processedCount,
    int successCount,
    int failureCount,
    UUID initiatedBy,
    String errorSummary,
    boolean replayed
) {
}