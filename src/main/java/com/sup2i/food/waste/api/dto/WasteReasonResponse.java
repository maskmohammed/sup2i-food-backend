package com.sup2i.food.waste.api.dto;

import com.sup2i.food.waste.domain.WasteCategory;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WasteReasonResponse(
    UUID id,
    UUID organizationId,
    String code,
    String name,
    WasteCategory category,
    boolean requiresComment,
    boolean active,
    OffsetDateTime createdAt
) {
}