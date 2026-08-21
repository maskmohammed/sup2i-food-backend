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
@Table(name = "product_dietary_tags")
public class ProductDietaryTag {

    @EmbeddedId
    private ProductDietaryTagId id;

    @MapsId("productId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "product_id",
        nullable = false
    )
    private Product product;

    @MapsId("dietaryTagId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "dietary_tag_id",
        nullable = false
    )
    private DietaryTag dietaryTag;

    @Column(name = "note")
    private String note;

    protected ProductDietaryTag() {
    }

    public ProductDietaryTag(
        Product product,
        DietaryTag dietaryTag,
        String note
    ) {
        this.id =
            new ProductDietaryTagId(
                product.getId(),
                dietaryTag.getId()
            );

        this.product = product;
        this.dietaryTag = dietaryTag;
        this.note = note;
    }

    public ProductDietaryTagId getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public DietaryTag getDietaryTag() {
        return dietaryTag;
    }

    public String getNote() {
        return note;
    }
}