package com.sup2i.food.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "recipes")
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "product_id",
        nullable = false
    )
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Column(
        name = "version",
        nullable = false
    )
    private int version;

    @Column(
        name = "is_active",
        nullable = false
    )
    private boolean active = true;

    @Column(
        name = "effective_from",
        nullable = false
    )
    private OffsetDateTime effectiveFrom;

    @Column(name = "effective_to")
    private OffsetDateTime effectiveTo;

    @CreationTimestamp
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(
        name = "updated_at",
        nullable = false
    )
    private OffsetDateTime updatedAt;

    protected Recipe() {
    }

    public Recipe(
        Product product,
        ProductVariant variant,
        int version,
        OffsetDateTime effectiveFrom
    ) {
        this.product = product;
        this.variant = variant;
        this.version = version;
        this.effectiveFrom = effectiveFrom;
    }

    public void close(
        OffsetDateTime effectiveTo
    ) {
        this.active = false;
        this.effectiveTo = effectiveTo;
    }

    public UUID getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public ProductVariant getVariant() {
        return variant;
    }

    public int getVersion() {
        return version;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getEffectiveFrom() {
        return effectiveFrom;
    }

    public OffsetDateTime getEffectiveTo() {
        return effectiveTo;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}