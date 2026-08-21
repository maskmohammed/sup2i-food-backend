package com.sup2i.food.catalog.api.dto;

import java.util.List;
import java.util.UUID;

public record MenuSectionResponse(
    UUID id,
    String code,
    String name,
    int minSelect,
    int maxSelect,
    int displayOrder,
    boolean active,
    List<MenuItemResponse> items
) {
}