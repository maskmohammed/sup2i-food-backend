package com.sup2i.food.catalog.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record UpsertProductDietaryMetadataRequest(

    @NotNull
    List<@Valid ProductReferenceLinkRequest> allergens,

    @NotNull
    List<@Valid ProductReferenceLinkRequest> dietaryTags
) {

    @AssertTrue(
        message =
            "allergens must not contain duplicate references"
    )
    public boolean isAllergensUnique() {

        if (allergens == null) {
            return true;
        }

        List<UUID> ids =
            allergens.stream()
                .map(
                    ProductReferenceLinkRequest::referenceId
                )
                .toList();

        return new HashSet<>(ids).size()
            == ids.size();
    }

    @AssertTrue(
        message =
            "dietaryTags must not contain duplicate references"
    )
    public boolean isDietaryTagsUnique() {

        if (dietaryTags == null) {
            return true;
        }

        List<UUID> ids =
            dietaryTags.stream()
                .map(
                    ProductReferenceLinkRequest::referenceId
                )
                .toList();

        return new HashSet<>(ids).size()
            == ids.size();
    }
}