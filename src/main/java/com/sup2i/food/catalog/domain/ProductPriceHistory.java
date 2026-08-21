package com.sup2i.food.catalog.domain;

import com.sup2i.food.identity.domain.User;
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
@Table(name = "product_price_history")
public class ProductPriceHistory {

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
        name = "product_id",
        nullable = false
    )
    private Product product;

    @Column(
        name = "price",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal price;

    @Column(
        name = "tax_rate",
        nullable = false,
        precision = 5,
        scale = 2
    )
    private BigDecimal taxRate;

    @Column(
        name = "effective_from",
        nullable = false
    )
    private OffsetDateTime effectiveFrom;

    @Column(
        name = "effective_to"
    )
    private OffsetDateTime effectiveTo;

    @ManyToOne(
        fetch = FetchType.LAZY
    )
    @JoinColumn(
        name = "changed_by"
    )
    private User changedBy;

    @Column(
        name = "reason",
        columnDefinition = "TEXT"
    )
    private String reason;

    @CreationTimestamp
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private OffsetDateTime createdAt;

    protected ProductPriceHistory() {
    }

    public ProductPriceHistory(
        Product product,
        BigDecimal price,
        BigDecimal taxRate,
        OffsetDateTime effectiveFrom,
        User changedBy,
        String reason
    ) {
        this.product = product;
        this.price = price;
        this.taxRate = taxRate;
        this.effectiveFrom =
            effectiveFrom;
        this.changedBy =
            changedBy;
        this.reason =
            reason;
    }

    public UUID getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public OffsetDateTime getEffectiveFrom() {
        return effectiveFrom;
    }

    public OffsetDateTime getEffectiveTo() {
        return effectiveTo;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}