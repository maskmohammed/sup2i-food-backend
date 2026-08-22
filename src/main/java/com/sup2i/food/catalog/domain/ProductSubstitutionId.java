package com.sup2i.food.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ProductSubstitutionId
    implements Serializable {

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "substitute_product_id")
    private UUID substituteProductId;

    protected ProductSubstitutionId() {
    }

    public ProductSubstitutionId(
        UUID productId,
        UUID substituteProductId
    ) {
        this.productId = productId;
        this.substituteProductId =
            substituteProductId;
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getSubstituteProductId() {
        return substituteProductId;
    }

    @Override
    public boolean equals(
        Object object
    ) {

        if (this == object) {
            return true;
        }

        if (
            object == null
            || getClass() != object.getClass()
        ) {
            return false;
        }

        ProductSubstitutionId that =
            (ProductSubstitutionId) object;

        return Objects.equals(
                productId,
                that.productId
            )
            && Objects.equals(
                substituteProductId,
                that.substituteProductId
            );
    }

    @Override
    public int hashCode() {

        return Objects.hash(
            productId,
            substituteProductId
        );
    }
}