package com.sup2i.food.reporting.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateReportSnapshotCommand(
    UUID campusId,
    UUID locationId,
    String reportType,
    OffsetDateTime periodStart,
    OffsetDateTime periodEnd,
    String dataJson,
    UUID generatedBy
) {
}