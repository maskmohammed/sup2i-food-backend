package com.sup2i.food.catalog.api.dto;

import java.util.List;
import java.util.UUID;

public record ProductOptionGroupResponse(
    UUID id,
    String name,
    int minSelect,
    int maxSelect,
    boolean required,
    int displayOrder,
    List<ProductOptionResponse> options
) {
}