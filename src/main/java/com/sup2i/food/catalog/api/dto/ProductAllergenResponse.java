package com.sup2i.food.catalog.api.dto;

import java.util.UUID;

public record ProductAllergenResponse(
    UUID id,
    String code,
    String name,
    String note
) {
}