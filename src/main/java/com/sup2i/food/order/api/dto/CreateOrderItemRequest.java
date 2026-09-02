package com.sup2i.food.order.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateOrderItemRequest(

    @NotNull
    UUID productId,

    UUID variantId,

    @Min(1)
    @Max(99)
    int quantity,

    List<
        @NotNull
        UUID
    > optionIds,

    @Size(max = 500)
    String specialInstructions
) {
}