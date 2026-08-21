package com.sup2i.food.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ProductAllergenId
    implements Serializable {

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "allergen_id")
    private UUID allergenId;

    protected ProductAllergenId() {
    }

    public ProductAllergenId(
        UUID productId,
        UUID allergenId
    ) {
        this.productId = productId;
        this.allergenId = allergenId;
    }

    public UUID getProductId() {
        return productId;
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
            !(object instanceof ProductAllergenId other)
        ) {
            return false;
        }

        return Objects.equals(
                productId,
                other.productId
            )
            && Objects.equals(
                allergenId,
                other.allergenId
            );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            productId,
            allergenId
        );
    }
}