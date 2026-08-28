package com.sup2i.food.promotion.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LoyaltyRedeemRequest(

    @NotNull
    UUID orderId,

    @Min(1)
    int points
) {
}