package com.sup2i.food.promotion.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AdminLoyaltyAdjustRequest(

    @NotNull
    UUID studentUserId,

    @NotNull
    Integer points,

    @NotNull
    @Size(max = 500)
    String reason
) {
}