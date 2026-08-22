package com.sup2i.food.inventory.api.dto;

import com.sup2i.food.inventory.domain.InventorySessionStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record InventorySessionResponse(
    UUID id,
    UUID stockLocationId,
    InventorySessionStatus status,
    UUID startedBy,
    OffsetDateTime startedAt,
    UUID completedBy,
    OffsetDateTime completedAt,
    UUID appliedBy,
    OffsetDateTime appliedAt,
    String notes,
    List<InventoryCountLineResponse> lines
) {
}