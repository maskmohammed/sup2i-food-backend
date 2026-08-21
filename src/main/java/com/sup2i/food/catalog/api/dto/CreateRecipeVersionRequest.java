package com.sup2i.food.catalog.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record CreateRecipeVersionRequest(

    UUID variantId,

    @NotEmpty
    List<@Valid RecipeItemRequest> items
) {

    @AssertTrue(
        message =
            "items must not contain duplicate ingredients"
    )
    public boolean isIngredientReferencesUnique() {

        if (items == null) {
            return true;
        }

        List<UUID> ids =
            items.stream()
                .map(
                    RecipeItemRequest::ingredientId
                )
                .toList();

        return new HashSet<>(ids).size()
            == ids.size();
    }
}