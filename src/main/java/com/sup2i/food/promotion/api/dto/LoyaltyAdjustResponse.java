package com.sup2i.food.promotion.api.dto;

import java.util.UUID;

public record LoyaltyAdjustResponse(
    UUID studentUserId,
    int points,
    int newBalance
) {
}