package com.sup2i.food.catalog.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCategoryRequest(

    @NotBlank
    @Size(max = 120)
    String name,

    @Size(max = 140)
    String slug,

    UUID parentId,

    @Min(0)
    int displayOrder,

    Boolean active
) {
}