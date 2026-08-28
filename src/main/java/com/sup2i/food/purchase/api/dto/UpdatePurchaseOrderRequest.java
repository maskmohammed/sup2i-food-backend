package com.sup2i.food.purchase.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdatePurchaseOrderRequest(

    String notes,

    @NotEmpty
    @Size(max = 100)
    List<
        @Valid CreatePurchaseOrderLineRequest
    > lines
) {
}