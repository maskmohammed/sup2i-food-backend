package com.sup2i.food.order.domain;

import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.domain.ProductVariant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    private UUID id;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "order_id",
        nullable = false
    )
    private Order order;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "product_id",
        nullable = false
    )
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Column(
        name = "product_name_snapshot",
        nullable = false,
        length = 180
    )
    private String productNameSnapshot;

    @Column(
        name = "unit_price",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal unitPrice;

    @Column(
        name = "quantity",
        nullable = false
    )
    private int quantity;

    @Column(
        name = "discount_amount",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal discountAmount =
        BigDecimal.ZERO;

    @Column(
        name = "line_total",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal lineTotal;

    @Column(
        name = "special_instructions",
        columnDefinition = "text"
    )
    private String specialInstructions;

    @Column(
        name = "variant_name_snapshot",
        length = 120
    )
    private String variantNameSnapshot;

    @Column(
        name = "sku_snapshot",
        length = 80
    )
    private String skuSnapshot;

    @Column(
        name = "tax_rate_snapshot",
        nullable = false,
        precision = 5,
        scale = 2
    )
    private BigDecimal taxRateSnapshot =
        BigDecimal.ZERO;

    @Column(
        name = "line_tax",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal lineTax =
        BigDecimal.ZERO;

    @Column(name = "group_order_member_id")
    private UUID groupOrderMemberId;

    protected OrderItem() {
    }

    public OrderItem(
        UUID id,
        Order order,
        Product product,
        ProductVariant variant,
        String productNameSnapshot,
        String variantNameSnapshot,
        String skuSnapshot,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal discountAmount,
        BigDecimal lineTotal,
        BigDecimal taxRateSnapshot,
        BigDecimal lineTax,
        String specialInstructions
    ) {
        this.id =
            id;

        this.order =
            order;

        this.product =
            product;

        this.variant =
            variant;

        this.productNameSnapshot =
            productNameSnapshot;

        this.variantNameSnapshot =
            variantNameSnapshot;

        this.skuSnapshot =
            skuSnapshot;

        this.unitPrice =
            unitPrice;

        this.quantity =
            quantity;

        this.discountAmount =
            discountAmount;

        this.lineTotal =
            lineTotal;

        this.taxRateSnapshot =
            taxRateSnapshot;

        this.lineTax =
            lineTax;

        this.specialInstructions =
            specialInstructions;
    }

    public UUID getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public Product getProduct() {
        return product;
    }

    public ProductVariant getVariant() {
        return variant;
    }

    public String getProductNameSnapshot() {
        return productNameSnapshot;
    }

    public String getVariantNameSnapshot() {
        return variantNameSnapshot;
    }

    public String getSkuSnapshot() {
        return skuSnapshot;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public BigDecimal getTaxRateSnapshot() {
        return taxRateSnapshot;
    }

    public BigDecimal getLineTax() {
        return lineTax;
    }

    public String getSpecialInstructions() {
        return specialInstructions;
    }

    public UUID getGroupOrderMemberId() {
        return groupOrderMemberId;
    }
}