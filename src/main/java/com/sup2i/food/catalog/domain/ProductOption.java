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
@Table(name = "product_options")
public class ProductOption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_group_id", nullable = false)
    private ProductOptionGroup optionGroup;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(
        name = "price_delta",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal priceDelta = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected ProductOption() {
    }

    public ProductOption(
        ProductOptionGroup optionGroup,
        String name,
        BigDecimal priceDelta,
        boolean active,
        int displayOrder
    ) {
        this.optionGroup = optionGroup;
        this.name = name;
        this.priceDelta = priceDelta;
        this.active = active;
        this.displayOrder = displayOrder;
    }

    public UUID getId() {
        return id;
    }

    public ProductOptionGroup getOptionGroup() {
        return optionGroup;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPriceDelta() {
        return priceDelta;
    }

    public boolean isActive() {
        return active;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }
}