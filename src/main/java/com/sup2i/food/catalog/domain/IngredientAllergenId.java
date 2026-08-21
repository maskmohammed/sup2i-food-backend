package com.sup2i.food.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class IngredientAllergenId
    implements Serializable {

    @Column(name = "ingredient_id")
    private UUID ingredientId;

    @Column(name = "allergen_id")
    private UUID allergenId;

    protected IngredientAllergenId() {
    }

    public IngredientAllergenId(
        UUID ingredientId,
        UUID allergenId
    ) {
        this.ingredientId = ingredientId;
        this.allergenId = allergenId;
    }

    public UUID getIngredientId() {
        return ingredientId;
    }

    public UUID getAllergenId() {
        return allergenId;
    }

    @Override
    public boolean equals(
        Object object
    ) {
        if (this == object) {
            return true;
        }

        if (
            !(object instanceof IngredientAllergenId other)
        ) {
            return false;
        }

        return Objects.equals(
                ingredientId,
                other.ingredientId
            )
            && Objects.equals(
                allergenId,
                other.allergenId
            );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            ingredientId,
            allergenId
        );
    }
}