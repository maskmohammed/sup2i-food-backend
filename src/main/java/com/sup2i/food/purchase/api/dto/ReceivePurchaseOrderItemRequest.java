package com.sup2i.food.purchase.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReceivePurchaseOrderItemRequest(

    @NotNull
    UUID purchaseOrderLineId,

    @NotNull
    @DecimalMin("0.001")
    @Digits(
        integer = 11,
        fraction = 3
    )
    BigDecimal quantity,

    @DecimalMin("0.00")
    @Digits(
        integer = 10,
        fraction = 4
    )
    BigDecimal unitCost,

    @Size(max = 120)
    String lotNumber,

    OffsetDateTime expiresAt
) {
}