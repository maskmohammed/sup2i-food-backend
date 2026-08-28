package com.sup2i.food.purchase.domain;

import com.sup2i.food.catalog.domain.Ingredient;
import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.domain.ProductVariant;
import com.sup2i.food.common.domain.MeasurementUnit;
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

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_items")
public class PurchaseOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "purchase_order_id",
        nullable = false
    )
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    @Column(
        name = "quantity",
        nullable = false,
        precision = 14,
        scale = 3
    )
    private BigDecimal quantity;

    @Column(
        name = "received_quantity",
        nullable = false,
        precision = 14,
        scale = 3
    )
    private BigDecimal receivedQuantity =
        BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "unit",
        nullable = false,
        length = 20
    )
    private MeasurementUnit unit;

    @Column(
        name = "unit_price",
        precision = 12,
        scale = 2
    )
    private BigDecimal unitPrice;

    @Column(
        name = "line_total",
        precision = 12,
        scale = 2
    )
    private BigDecimal lineTotal;

    protected PurchaseOrderLine() {
    }

    public PurchaseOrderLine(
        PurchaseOrder purchaseOrder,
        Product product,
        ProductVariant variant,
        Ingredient ingredient,
        BigDecimal quantity,
        MeasurementUnit unit,
        BigDecimal unitPrice,
        BigDecimal lineTotal
    ) {
        this.purchaseOrder = purchaseOrder;
        this.product = product;
        this.variant = variant;
        this.ingredient = ingredient;
        this.quantity = quantity;
        this.unit = unit;
        this.unitPrice = unitPrice;
        this.lineTotal = lineTotal;
    }

    public void applyReceived(
        BigDecimal quantity
    ) {
        if (
            quantity == null
            || quantity.signum() <= 0
        ) {
            throw new IllegalArgumentException(
                "Received quantity must be positive."
            );
        }

        BigDecimal updated =
            receivedQuantity.add(quantity);

        if (
            updated.compareTo(this.quantity)
            > 0
        ) {
            throw new IllegalArgumentException(
                "Received quantity exceeds ordered quantity."
            );
        }

        this.receivedQuantity =
            updated;
    }

    public boolean hasRemaining() {
        return receivedQuantity
            .compareTo(quantity)
            < 0;
    }

    public BigDecimal remainingQuantity() {
        return quantity.subtract(
            receivedQuantity
        );
    }

    public UUID getId() {
        return id;
    }

    public PurchaseOrder getPurchaseOrder() {
        return purchaseOrder;
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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getReceivedQuantity() {
        return receivedQuantity;
    }

    public MeasurementUnit getUnit() {
        return unit;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}