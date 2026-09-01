package com.sup2i.food.integration.api.dto;

import com.sup2i.food.integration.domain.ImportJobStatus;
import com.sup2i.food.integration.domain.ImportType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ImportJobResponse(
    UUID id,
    UUID organizationId,
    ImportType importType,
    UUID sourceFileAssetId,
    ImportJobStatus status,
    UUID requestedBy,
    OffsetDateTime createdAt,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt,
    int totalRows,
    int successRows,
    int failedRows,
    String errorSummary,
    boolean replayed
) {
}