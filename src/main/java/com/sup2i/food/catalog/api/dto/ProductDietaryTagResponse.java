package com.sup2i.food.catalog.api.dto;

import java.util.UUID;

public record ProductDietaryTagResponse(
    UUID id,
    String code,
    String name,
    String note
) {
}