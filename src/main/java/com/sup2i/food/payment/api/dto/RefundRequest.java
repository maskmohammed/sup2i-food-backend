package com.sup2i.food.payment.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RefundRequest(

    @NotNull
    @DecimalMin("0.01")
    @Digits(
        integer = 10,
        fraction = 2
    )
    BigDecimal amount,

    @NotBlank
    @Size(
        min = 3,
        max = 500
    )
    String reason
) {
}