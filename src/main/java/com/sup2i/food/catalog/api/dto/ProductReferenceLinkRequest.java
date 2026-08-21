package com.sup2i.food.catalog.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ProductReferenceLinkRequest(

    @NotNull
    UUID referenceId,

    String note
) {
}