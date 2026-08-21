package com.sup2i.food.catalog.api.dto;

import java.util.UUID;

public record DietaryReferenceResponse(
    UUID id,
    String code,
    String name,
    String description
) {
}