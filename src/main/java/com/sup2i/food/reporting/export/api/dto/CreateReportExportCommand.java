package com.sup2i.food.reporting.export.api.dto;

import com.sup2i.food.reporting.export.domain.ReportExportType;

import java.util.UUID;

public record CreateReportExportCommand(
    UUID reportSnapshotId,
    ReportExportType exportType,
    UUID requestedBy,
    String parametersJson
) {
}