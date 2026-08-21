package com.sup2i.food.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_allergens")
public class ProductAllergen {

    @EmbeddedId
    private ProductAllergenId id;

    @MapsId("productId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "product_id",
        nullable = false
    )
    private Product product;

    @MapsId("allergenId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "allergen_id",
        nullable = false
    )
    private Allergen allergen;

    @Column(name = "note")
    private String note;

    protected ProductAllergen() {
    }

    public ProductAllergen(
        Product product,
        Allergen allergen,
        String note
    ) {
        this.id =
            new ProductAllergenId(
                product.getId(),
                allergen.getId()
            );

        this.product = product;
        this.allergen = allergen;
        this.note = note;
    }

    public ProductAllergenId getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public Allergen getAllergen() {
        return allergen;
    }

    public String getNote() {
        return note;
    }
}