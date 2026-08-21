package com.sup2i.food.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ProductDietaryTagId
    implements Serializable {

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "dietary_tag_id")
    private UUID dietaryTagId;

    protected ProductDietaryTagId() {
    }

    public ProductDietaryTagId(
        UUID productId,
        UUID dietaryTagId
    ) {
        this.productId = productId;
        this.dietaryTagId = dietaryTagId;
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getDietaryTagId() {
        return dietaryTagId;
    }

    @Override
    public boolean equals(
        Object object
    ) {

        if (this == object) {
            return true;
        }

        if (
            !(object instanceof ProductDietaryTagId other)
        ) {
            return false;
        }

        return Objects.equals(
                productId,
                other.productId
            )
            && Objects.equals(
                dietaryTagId,
                other.dietaryTagId
            );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            productId,
            dietaryTagId
        );
    }
}