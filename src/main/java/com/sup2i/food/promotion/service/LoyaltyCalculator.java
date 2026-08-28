package com.sup2i.food.promotion.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class LoyaltyCalculator {

    private LoyaltyCalculator() {
    }

    public static int earnedPoints(
        BigDecimal amountInMad,
        BigDecimal madPerPoint
    ) {
        if (
            amountInMad == null
                || madPerPoint == null
                || madPerPoint.signum() <= 0
                || amountInMad.signum() <= 0
        ) {
            return 0;
        }

        BigDecimal counts =
            amountInMad.divide(
                madPerPoint,
                0,
                RoundingMode.FLOOR
            );

        return counts.intValueExact();
    }

    public static LoyaltyRedemption redeem(
        int points,
        int pointsPerReward,
        BigDecimal rewardValueMad
    ) {
        BigDecimal zero =
            BigDecimal.ZERO.setScale(2);

        if (
            pointsPerReward <= 0
                || points < pointsPerReward
                || rewardValueMad == null
                || rewardValueMad.signum() < 0
        ) {
            return new LoyaltyRedemption(
                0,
                0,
                zero
            );
        }

        int units =
            points / pointsPerReward;

        int usedPoints =
            units * pointsPerReward;

        BigDecimal reward =
            rewardValueMad
                .multiply(
                    BigDecimal.valueOf(units)
                )
                .setScale(
                    2,
                    RoundingMode.HALF_UP
                );

        return new LoyaltyRedemption(
            units,
            usedPoints,
            reward
        );
    }
}