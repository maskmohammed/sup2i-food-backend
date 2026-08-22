package com.sup2i.food.catalog.api.dto;

import jakarta.validation.constraints.Min;

public record UpsertProductSubstitutionRequest(

    @Min(0)
    Integer priority,

    Boolean active
) {
}