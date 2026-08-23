package com.sup2i.food.order.api.dto;

public record OrderMutationResponse(
    OrderResponse order,
    boolean replayed
) {
}