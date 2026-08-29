package com.sup2i.food.kitchen.service.routing;

import com.sup2i.food.catalog.domain.Category;
import com.sup2i.food.catalog.domain.Product;
import com.sup2i.food.catalog.domain.ProductVariant;
import com.sup2i.food.order.domain.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

public record KitchenRoutingUnit(
    OrderItem orderItem,
    UUID menuSelectionId,
    Product product,
    ProductVariant variant,
    BigDecimal quantity
) {

    public KitchenRoutingUnit {

        if (orderItem == null) {
            throw new IllegalArgumentException(
                "Order item is required for kitchen routing."
            );
        }

        if (product == null) {
            throw new IllegalArgumentException(
                "Product is required for kitchen routing."
            );
        }

        if (
            quantity == null
            || quantity.signum() <= 0
        ) {
            throw new IllegalArgumentException(
                "Kitchen routing quantity must be positive."
            );
        }
    }

    public UUID orderItemId() {
        return orderItem.getId();
    }

    public UUID productId() {
        return product.getId();
    }

    public UUID variantId() {

        return variant == null
            ? null
            : variant.getId();
    }

    public UUID categoryId() {

        Category category =
            product.getCategory();

        return category == null
            ? null
            : category.getId();
    }
}
