package com.sup2i.food.order.domain;

import com.sup2i.food.catalog.domain.MenuItem;
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
@Table(name = "order_item_menu_selections")
public class OrderItemMenuSelection {

    @Id
    private UUID id;

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "order_item_id",
        nullable = false
    )
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_item_id")
    private MenuItem menuItem;

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
        name = "variant_name_snapshot",
        length = 120
    )
    private String variantNameSnapshot;

    @Column(
        nullable = false,
        precision = 14,
        scale = 3
    )
    private BigDecimal quantity;

    @Column(
        name = "price_delta_snapshot",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal priceDeltaSnapshot;

    protected OrderItemMenuSelection() {
    }

    public UUID getId() {
        return id;
    }

    public OrderItem getOrderItem() {
        return orderItem;
    }

    public MenuItem getMenuItem() {
        return menuItem;
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

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPriceDeltaSnapshot() {
        return priceDeltaSnapshot;
    }
}
