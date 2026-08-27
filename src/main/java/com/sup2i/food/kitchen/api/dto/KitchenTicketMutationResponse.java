package com.sup2i.food.kitchen.api.dto;

public record KitchenTicketMutationResponse(
    KitchenTicketResponse ticket,
    boolean replayed
) {
}
