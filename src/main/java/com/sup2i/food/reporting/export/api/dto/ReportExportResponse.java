package com.sup2i.food.reporting.export.api.dto;

import com.sup2i.food.reporting.export.domain.ReportExportStatus;
import com.sup2i.food.reporting.export.domain.ReportExportType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReportExportResponse(
    UUID id,
    UUID reportSnapshotId,
    UUID organizationId,
    ReportExportType exportType,
    ReportExportStatus status,
    UUID requestedBy,
    OffsetDateTime requestedAt,
    OffsetDateTime completedAt,
    UUID fileAssetId,
    String parametersJson,
    String errorMessage,
    boolean replayed
) {
}