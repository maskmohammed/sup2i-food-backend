package com.sup2i.food.catalog.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record ReplaceIngredientAllergensRequest(

    @NotNull
    List<@NotNull UUID> allergenIds
) {

    @AssertTrue(
        message =
            "allergenIds must not contain duplicates"
    )
    public boolean isAllergenReferencesUnique() {

        if (allergenIds == null) {
            return true;
        }

        return new HashSet<>(
            allergenIds
        ).size() == allergenIds.size();
    }
}