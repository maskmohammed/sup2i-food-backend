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

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "menu_items")
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "menu_section_id",
        nullable = false
    )
    private MenuSection menuSection;

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
        name = "quantity",
        nullable = false,
        precision = 14,
        scale = 3
    )
    private BigDecimal quantity =
        BigDecimal.ONE;

    @Column(
        name = "price_delta",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal priceDelta =
        BigDecimal.ZERO;

    @Column(
        name = "is_default",
        nullable = false
    )
    private boolean defaultItem;

    @Column(
        name = "is_active",
        nullable = false
    )
    private boolean active = true;

    @Column(
        name = "display_order",
        nullable = false
    )
    private int displayOrder;

    protected MenuItem() {
    }

    public MenuItem(
        MenuSection menuSection,
        Product product,
        ProductVariant variant,
        BigDecimal quantity,
        BigDecimal priceDelta,
        boolean defaultItem,
        boolean active,
        int displayOrder
    ) {
        this.menuSection = menuSection;
        this.product = product;
        this.variant = variant;
        this.quantity = quantity;
        this.priceDelta = priceDelta;
        this.defaultItem = defaultItem;
        this.active = active;
        this.displayOrder = displayOrder;
    }

    public UUID getId() {
        return id;
    }

    public MenuSection getMenuSection() {
        return menuSection;
    }

    public Product getProduct() {
        return product;
    }

    public ProductVariant getVariant() {
        return variant;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPriceDelta() {
        return priceDelta;
    }

    public boolean isDefaultItem() {
        return defaultItem;
    }

    public boolean isActive() {
        return active;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}