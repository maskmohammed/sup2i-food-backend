package com.sup2i.food.production.api.dto;

import com.sup2i.food.production.domain.ProductionRunStatus;
import com.sup2i.food.production.domain.ProductionTargetSource;
import com.sup2i.food.production.domain.ProductionType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ProductionRunResponse(
    UUID id,
    UUID organizationId,
    UUID campusId,
    UUID serviceLocationId,
    UUID kitchenLocationId,
    UUID canteenMenuId,
    UUID campusEventId,
    LocalDate productionDate,
    ProductionType productionType,
    ProductionRunStatus status,
    ProductionTargetSource targetSource,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt,
    OffsetDateTime cancelledAt,
    UUID createdBy,
    UUID approvedBy,
    String notes,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    List<ProductionRunItemResponse> items
) {
}