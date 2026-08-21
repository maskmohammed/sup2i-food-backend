package com.sup2i.food.catalog.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "ingredient_allergens")
public class IngredientAllergen {

    @EmbeddedId
    private IngredientAllergenId id;

    @MapsId("ingredientId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "ingredient_id",
        nullable = false
    )
    private Ingredient ingredient;

    @MapsId("allergenId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "allergen_id",
        nullable = false
    )
    private Allergen allergen;

    protected IngredientAllergen() {
    }

    public IngredientAllergen(
        Ingredient ingredient,
        Allergen allergen
    ) {
        this.id =
            new IngredientAllergenId(
                ingredient.getId(),
                allergen.getId()
            );

        this.ingredient = ingredient;
        this.allergen = allergen;
    }

    public IngredientAllergenId getId() {
        return id;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public Allergen getAllergen() {
        return allergen;
    }
}