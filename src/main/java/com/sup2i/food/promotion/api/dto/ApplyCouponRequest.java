package com.sup2i.food.promotion.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ApplyCouponRequest(

    @NotNull
    UUID orderId,

    @NotBlank
    String code
) {
}