package com.sup2i.food.inventory.domain;

import com.sup2i.food.catalog.domain.Ingredient;
import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.domain.ProductVariant;
import com.sup2i.food.common.domain.MeasurementUnit;
import com.sup2i.food.organization.domain.Organization;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_items")
public class StockItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "organization_id",
        nullable = false
    )
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "base_unit",
        nullable = false,
        length = 20
    )
    private MeasurementUnit baseUnit;

    @Column(
        name = "low_stock_threshold",
        precision = 14,
        scale = 3
    )
    private BigDecimal lowStockThreshold;

    @Column(
        name = "track_expiry",
        nullable = false
    )
    private boolean trackExpiry;

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

    protected StockItem() {
    }

    public StockItem(
        Organization organization,
        Product product,
        ProductVariant variant,
        Ingredient ingredient,
        MeasurementUnit baseUnit,
        BigDecimal lowStockThreshold,
        boolean trackExpiry
    ) {
        this.organization = organization;
        this.product = product;
        this.variant = variant;
        this.ingredient = ingredient;
        this.baseUnit = baseUnit;
        this.lowStockThreshold =
            lowStockThreshold;
        this.trackExpiry = trackExpiry;
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Product getProduct() {
        return product;
    }

    public ProductVariant getVariant() {
        return variant;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public MeasurementUnit getBaseUnit() {
        return baseUnit;
    }

    public BigDecimal getLowStockThreshold() {
        return lowStockThreshold;
    }

    public boolean isTrackExpiry() {
        return trackExpiry;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void updateConfiguration(
        BigDecimal lowStockThreshold,
        boolean trackExpiry
    ) {
        this.lowStockThreshold =
            lowStockThreshold;

        this.trackExpiry =
            trackExpiry;
    }
}