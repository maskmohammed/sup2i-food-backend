package com.sup2i.food.catalog.api.dto;

import java.util.UUID;

public record CategoryResponse(
    UUID id,
    UUID parentId,
    String name,
    String slug,
    int displayOrder,
    boolean active
) {
}