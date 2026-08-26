package com.sup2i.food.payment.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePaymentRequest(

    @NotNull(
        message = "orderId is required."
    )
    UUID orderId,

    @NotNull(
        message = "method is required."
    )
    PaymentMethodRequest method,

    @NotNull(
        message = "amount is required."
    )
    @DecimalMin(
        value = "0.01",
        inclusive = true,
        message = "amount must be at least 0.01."
    )
    @Digits(
        integer = 10,
        fraction = 2,
        message = "amount must contain at most 10 integer digits and 2 decimal places."
    )
    BigDecimal amount,

    @Size(
        max = 160,
        message = "externalReference must contain at most 160 characters."
    )
    String externalReference,

    UUID posSessionId

) {
}