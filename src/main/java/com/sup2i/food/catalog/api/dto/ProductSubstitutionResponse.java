package com.sup2i.food.catalog.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductSubstitutionResponse(
    UUID productId,
    UUID substituteProductId,
    String substituteProductName,
    int priority,
    boolean active,
    OffsetDateTime createdAt
) {
}