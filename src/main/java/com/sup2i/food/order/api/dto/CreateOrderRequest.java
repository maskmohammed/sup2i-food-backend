package com.sup2i.food.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(

    @NotNull
    UUID locationId,

    UUID slotId,

    @NotNull
    @Size(
        min = 1,
        max = 50
    )
    List<
        @Valid
        CreateOrderItemRequest
    > items
) {
}