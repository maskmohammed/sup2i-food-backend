package com.sup2i.food.catalog.domain;

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
@Table(name = "product_option_components")
public class ProductOptionComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "product_option_id",
        nullable = false
    )
    private ProductOption productOption;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_product_id")
    private Product componentProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_variant_id")
    private ProductVariant componentVariant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    @Column(
        name = "quantity",
        nullable = false,
        precision = 14,
        scale = 3
    )
    private BigDecimal quantity =
        BigDecimal.ONE;

    @Enumerated(EnumType.STRING)
    @Column(
        name = "unit",
        nullable = false,
        length = 20
    )
    private MeasurementUnit unit =
        MeasurementUnit.PIECE;

    protected ProductOptionComponent() {
    }

    public ProductOptionComponent(
        ProductOption productOption,
        Product componentProduct,
        ProductVariant componentVariant,
        Ingredient ingredient,
        BigDecimal quantity,
        MeasurementUnit unit
    ) {
        this.productOption = productOption;
        this.componentProduct = componentProduct;
        this.componentVariant = componentVariant;
        this.ingredient = ingredient;
        this.quantity = quantity;
        this.unit = unit;
    }

    public UUID getId() {
        return id;
    }

    public ProductOption getProductOption() {
        return productOption;
    }

    public Product getComponentProduct() {
        return componentProduct;
    }

    public ProductVariant getComponentVariant() {
        return componentVariant;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public MeasurementUnit getUnit() {
        return unit;
    }
}