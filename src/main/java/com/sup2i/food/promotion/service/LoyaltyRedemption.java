package com.sup2i.food.promotion.service;

import java.math.BigDecimal;

public record LoyaltyRedemption(
    int rewardUnits,
    int usedPoints,
    BigDecimal rewardValue
) {
}