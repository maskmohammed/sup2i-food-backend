package com.sup2i.food.catalog.domain;

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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "products",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_products_org_sku",
            columnNames = {
                "organization_id",
                "sku"
            }
        )
    }
)
public class Product {

    @Id
    @GeneratedValue(
        strategy = GenerationType.UUID
    )
    private UUID id;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "organization_id",
        nullable = false
    )
    private Organization organization;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "category_id",
        nullable = false
    )
    private Category category;

    @Column(
        name = "sku",
        nullable = false,
        length = 80
    )
    private String sku;

    @Column(
        name = "name",
        nullable = false,
        length = 180
    )
    private String name;

    @Column(
        name = "description",
        columnDefinition = "TEXT"
    )
    private String description;

    @Column(
        name = "image_url",
        columnDefinition = "TEXT"
    )
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "product_type",
        nullable = false,
        length = 30
    )
    private ProductType productType;

    @Column(
        name = "base_price",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal basePrice;

    @Column(
        name = "tax_rate",
        nullable = false,
        precision = 5,
        scale = 2
    )
    private BigDecimal taxRate =
        BigDecimal.ZERO;

    @Column(
        name = "preparation_minutes"
    )
    private Integer preparationMinutes;

    @Column(
        name = "track_stock",
        nullable = false
    )
    private boolean trackStock = true;

    @Column(
        name = "is_prepared",
        nullable = false
    )
    private boolean prepared;

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

    @UpdateTimestamp
    @Column(
        name = "updated_at",
        nullable = false
    )
    private OffsetDateTime updatedAt;

    protected Product() {
    }

    public Product(
        Organization organization,
        Category category,
        String sku,
        String name,
        String description,
        String imageUrl,
        ProductType productType,
        BigDecimal basePrice,
        BigDecimal taxRate,
        Integer preparationMinutes,
        boolean trackStock,
        boolean prepared,
        boolean active
    ) {
        this.organization =
            organization;

        this.category =
            category;

        this.sku =
            sku;

        this.name =
            name;

        this.description =
            description;

        this.imageUrl =
            imageUrl;

        this.productType =
            productType;

        this.basePrice =
            basePrice;

        this.taxRate =
            taxRate;

        this.preparationMinutes =
            preparationMinutes;

        this.trackStock =
            trackStock;

        this.prepared =
            prepared;

        this.active =
            active;
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(
        Category category
    ) {
        this.category = category;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(
        String sku
    ) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(
        String name
    ) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(
        String description
    ) {
        this.description =
            description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(
        String imageUrl
    ) {
        this.imageUrl = imageUrl;
    }

    public ProductType getProductType() {
        return productType;
    }

    public void setProductType(
        ProductType productType
    ) {
        this.productType =
            productType;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(
        BigDecimal basePrice
    ) {
        this.basePrice =
            basePrice;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(
        BigDecimal taxRate
    ) {
        this.taxRate = taxRate;
    }

    public Integer getPreparationMinutes() {
        return preparationMinutes;
    }

    public void setPreparationMinutes(
        Integer preparationMinutes
    ) {
        this.preparationMinutes =
            preparationMinutes;
    }

    public boolean isTrackStock() {
        return trackStock;
    }

    public void setTrackStock(
        boolean trackStock
    ) {
        this.trackStock =
            trackStock;
    }

    public boolean isPrepared() {
        return prepared;
    }

    public void setPrepared(
        boolean prepared
    ) {
        this.prepared =
            prepared;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(
        boolean active
    ) {
        this.active = active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}