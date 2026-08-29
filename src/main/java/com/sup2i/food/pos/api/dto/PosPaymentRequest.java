package com.sup2i.food.pos.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record PosPaymentRequest(

    @NotNull
    UUID orderId,

    @NotNull
    UUID posSessionId,

    @NotNull
    PosPaymentMethodRequest method,

    @DecimalMin("0.00")
    @Digits(
        integer = 10,
        fraction = 2
    )
    BigDecimal tenderedAmount,

    @Size(max = 160)
    String externalReference
) {
}