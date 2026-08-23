package com.sup2i.food.order.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpsertOrderItemRequest(

    @NotNull
    UUID productId,

    UUID variantId,

    @Min(1)
    @Max(99)
    int quantity,

    @Size(max = 1000)
    String specialInstructions
) {
}