package com.sup2i.food.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "product_substitutions")
public class ProductSubstitution {

    @EmbeddedId
    private ProductSubstitutionId id;

    @MapsId("productId")
    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "product_id",
        nullable = false
    )
    private Product product;

    @MapsId("substituteProductId")
    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "substitute_product_id",
        nullable = false
    )
    private Product substituteProduct;

    @Column(
        name = "priority",
        nullable = false
    )
    private int priority;

    @Column(
        name = "is_active",
        nullable = false
    )
    private boolean active = true;

    @CreationTimestamp
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime createdAt;

    protected ProductSubstitution() {
    }

    public ProductSubstitution(
        Product product,
        Product substituteProduct,
        int priority,
        boolean active
    ) {
        this.id =
            new ProductSubstitutionId(
                product.getId(),
                substituteProduct.getId()
            );

        this.product = product;
        this.substituteProduct =
            substituteProduct;

        this.priority = priority;
        this.active = active;
    }

    public ProductSubstitutionId getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public Product getSubstituteProduct() {
        return substituteProduct;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void update(
        int priority,
        boolean active
    ) {
        this.priority = priority;
        this.active = active;
    }
}