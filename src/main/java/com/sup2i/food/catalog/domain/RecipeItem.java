package com.sup2i.food.catalog.domain;

import com.sup2i.food.common.domain.MeasurementUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "recipe_items")
public class RecipeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "recipe_id",
        nullable = false
    )
    private Recipe recipe;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "ingredient_id",
        nullable = false
    )
    private Ingredient ingredient;

    @Column(
        name = "quantity",
        nullable = false,
        precision = 14,
        scale = 3
    )
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "unit",
        nullable = false,
        length = 20
    )
    private MeasurementUnit unit;

    @Column(
        name = "waste_factor",
        precision = 6,
        scale = 4
    )
    private BigDecimal wasteFactor;

    @Column(
        name = "is_critical",
        nullable = false
    )
    private boolean critical = true;

    protected RecipeItem() {
    }

    public RecipeItem(
        Recipe recipe,
        Ingredient ingredient,
        BigDecimal quantity,
        MeasurementUnit unit,
        BigDecimal wasteFactor,
        boolean critical
    ) {
        this.recipe = recipe;
        this.ingredient = ingredient;
        this.quantity = quantity;
        this.unit = unit;
        this.wasteFactor = wasteFactor;
        this.critical = critical;
    }

    public UUID getId() {
        return id;
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public MeasurementUnit getUnit() {
        return unit;
    }

    public BigDecimal getWasteFactor() {
        return wasteFactor;
    }

    public boolean isCritical() {
        return critical;
    }
}