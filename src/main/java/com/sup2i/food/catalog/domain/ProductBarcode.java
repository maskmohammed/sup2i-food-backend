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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "product_barcodes")
public class ProductBarcode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Column(
        name = "barcode",
        nullable = false,
        unique = true,
        length = 120
    )
    private String barcode;

    @Column(
        name = "pack_quantity",
        nullable = false,
        precision = 14,
        scale = 3
    )
    private BigDecimal packQuantity = BigDecimal.ONE;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected ProductBarcode() {
    }

    public ProductBarcode(
        Product product,
        ProductVariant variant,
        String barcode,
        BigDecimal packQuantity,
        boolean primary,
        boolean active
    ) {
        this.product = product;
        this.variant = variant;
        this.barcode = barcode;
        this.packQuantity = packQuantity;
        this.primary = primary;
        this.active = active;
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

    public String getBarcode() {
        return barcode;
    }

    public BigDecimal getPackQuantity() {
        return packQuantity;
    }

    public boolean isPrimary() {
        return primary;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}