package com.sup2i.food.promotion.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LoyaltyRedeemResponse(
    UUID orderId,
    int pointsRedeemed,
    BigDecimal rewardMad,
    int newBalance,
    BigDecimal discountTotal,
    BigDecimal total
) {
}