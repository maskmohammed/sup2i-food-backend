package com.sup2i.food.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UpsertOrderRequest(

    @NotNull
    UUID locationId,

    @Pattern(
        regexp = "^[A-Za-z]{3}$"
    )
    String currency,

    @Size(max = 2000)
    String customerNote,

    @NotNull
    @Size(max = 50)
    List<
        @Valid
        UpsertOrderItemRequest
    > items,

    UUID timeSlotId
) {
}