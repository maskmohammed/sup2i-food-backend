package com.sup2i.food.reporting.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReportSnapshotResponse(
    UUID id,
    UUID organizationId,
    UUID campusId,
    UUID locationId,
    String reportType,
    OffsetDateTime periodStart,
    OffsetDateTime periodEnd,
    String dataJson,
    UUID generatedBy,
    OffsetDateTime generatedAt,
    boolean replayed
) {
}