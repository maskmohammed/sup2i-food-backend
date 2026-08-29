package com.sup2i.food.pos.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ForceClosePosSessionRequest(

    @DecimalMin("0.00")
    @Digits(
        integer = 10,
        fraction = 2
    )
    BigDecimal countedCash,

    @NotBlank
    @Size(max = 2000)
    String comment
) {
}