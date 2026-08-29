package com.sup2i.food.kitchen.domain;

import com.sup2i.food.catalog.domain.Category;
import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.domain.ProductVariant;
import com.sup2i.food.organization.domain.Location;
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
@Table(name = "preparation_routes")
public class PreparationRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "source_location_id",
        nullable = false
    )
    private Location sourceLocation;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "kitchen_location_id",
        nullable = false
    )
    private Location kitchenLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Column(
        nullable = false
    )
    private int priority;

    @Column(
        name = "is_active",
        nullable = false
    )
    private boolean active = true;

    @Column(name = "valid_from")
    private OffsetDateTime validFrom;

    @Column(name = "valid_to")
    private OffsetDateTime validTo;

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

    protected PreparationRoute() {
    }

    public PreparationRoute(
        Location sourceLocation,
        Location kitchenLocation,
        Category category,
        Product product,
        ProductVariant variant,
        int priority,
        boolean active,
        OffsetDateTime validFrom,
        OffsetDateTime validTo
    ) {
        this.sourceLocation = sourceLocation;
        this.kitchenLocation = kitchenLocation;
        this.category = category;
        this.product = product;
        this.variant = variant;
        this.priority = priority;
        this.active = active;
        this.validFrom = validFrom;
        this.validTo = validTo;
    }

    public UUID getId() {
        return id;
    }

    public Location getSourceLocation() {
        return sourceLocation;
    }

    public Location getKitchenLocation() {
        return kitchenLocation;
    }

    public Category getCategory() {
        return category;
    }

    public Product getProduct() {
        return product;
    }

    public ProductVariant getVariant() {
        return variant;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getValidFrom() {
        return validFrom;
    }

    public OffsetDateTime getValidTo() {
        return validTo;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isEffectiveAt(
        OffsetDateTime at
    ) {
        if (!active) {
            return false;
        }

        boolean beforeStart =
            validFrom != null
            && at.isBefore(validFrom);

        if (beforeStart) {
            return false;
        }

        boolean atOrAfterEnd =
            validTo != null
            && !at.isBefore(validTo);

        return !atOrAfterEnd;
    }

    public boolean matchesScope(
        UUID categoryId,
        UUID productId,
        UUID variantId
    ) {
        if (variant != null) {
            return variantId != null
                && variant.getId().equals(variantId);
        }

        if (product != null) {
            return productId != null
                && product.getId().equals(productId);
        }

        if (category != null) {
            return categoryId != null
                && category.getId().equals(categoryId);
        }

        return true;
    }
}