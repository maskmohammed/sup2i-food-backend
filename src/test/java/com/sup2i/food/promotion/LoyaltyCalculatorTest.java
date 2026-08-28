package com.sup2i.food.promotion;

import com.sup2i.food.promotion.service.LoyaltyCalculator;
import com.sup2i.food.promotion.service.LoyaltyRedemption;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoyaltyCalculatorTest {

    @Test
    void earnedPointsComputeFullUnits() {

        int points =
            LoyaltyCalculator.earnedPoints(
                new BigDecimal("100.00"),
                new BigDecimal("10")
            );

        assertThat(points)
            .isEqualTo(10);
    }

    @Test
    void earnedPointsFloorFractionalRemainder() {

        int points =
            LoyaltyCalculator.earnedPoints(
                new BigDecimal("99.99"),
                new BigDecimal("10")
            );

        assertThat(points)
            .isEqualTo(9);
    }

    @Test
    void earnedPointsOnTotalIncludingFractionalAmount() {

        int points =
            LoyaltyCalculator.earnedPoints(
                new BigDecimal("151.50"),
                new BigDecimal("10")
            );

        assertThat(points)
            .isEqualTo(15);
    }

    @Test
    void earnedPointsNonPositiveAmountReturnsZero() {

        int points =
            LoyaltyCalculator.earnedPoints(
                new BigDecimal("-5.00"),
                new BigDecimal("10")
            );

        assertThat(points)
            .isEqualTo(0);
    }

    @Test
    void earnedPointsNullAmountReturnsZero() {

        int points =
            LoyaltyCalculator.earnedPoints(
                null,
                new BigDecimal("10")
            );

        assertThat(points)
            .isEqualTo(0);
    }

    @Test
    void earnedPointsNonPositiveMadPerPointReturnsZero() {

        int points =
            LoyaltyCalculator.earnedPoints(
                new BigDecimal("100.00"),
                BigDecimal.ZERO
            );

        assertThat(points)
            .isEqualTo(0);
    }

    @Test
    void redeemSingleReward() {

        LoyaltyRedemption redemption =
            LoyaltyCalculator.redeem(
                100,
                100,
                new BigDecimal("5.00")
            );

        assertThat(redemption.rewardUnits())
            .isEqualTo(1);
        assertThat(redemption.usedPoints())
            .isEqualTo(100);
        assertThat(redemption.rewardValue())
            .isEqualByComparingTo("5.00");
    }

    @Test
    void redeemMultipleRewardUnits() {

        LoyaltyRedemption redemption =
            LoyaltyCalculator.redeem(
                250,
                100,
                new BigDecimal("5.00")
            );

        assertThat(redemption.rewardUnits())
            .isEqualTo(2);
        assertThat(redemption.usedPoints())
            .isEqualTo(200);
        assertThat(redemption.rewardValue())
            .isEqualByComparingTo("10.00");
    }

    @Test
    void redeemIgnoresPartialUnitRemainder() {

        LoyaltyRedemption redemption =
            LoyaltyCalculator.redeem(
                150,
                100,
                new BigDecimal("5.00")
            );

        assertThat(redemption.rewardUnits())
            .isEqualTo(1);
        assertThat(redemption.usedPoints())
            .isEqualTo(100);
        assertThat(redemption.rewardValue())
            .isEqualByComparingTo("5.00");
    }

    @Test
    void redeemBelowThresholdReturnsZero() {

        LoyaltyRedemption redemption =
            LoyaltyCalculator.redeem(
                99,
                100,
                new BigDecimal("5.00")
            );

        assertThat(redemption.rewardUnits())
            .isEqualTo(0);
        assertThat(redemption.usedPoints())
            .isEqualTo(0);
        assertThat(redemption.rewardValue())
            .isEqualByComparingTo("0.00");
    }

    @Test
    void redeemInvalidPointsPerRewardReturnsZero() {

        LoyaltyRedemption redemption =
            LoyaltyCalculator.redeem(
                100,
                0,
                new BigDecimal("5.00")
            );

        assertThat(redemption.rewardUnits())
            .isEqualTo(0);
        assertThat(redemption.rewardValue())
            .isEqualByComparingTo("0.00");
    }

    @Test
    void redeemNullRewardValueReturnsZero() {

        LoyaltyRedemption redemption =
            LoyaltyCalculator.redeem(
                100,
                100,
                null
            );

        assertThat(redemption.rewardUnits())
            .isEqualTo(0);
        assertThat(redemption.rewardValue())
            .isEqualByComparingTo("0.00");
    }

    @Test
    void redeemNegativeRewardValueReturnsZero() {

        LoyaltyRedemption redemption =
            LoyaltyCalculator.redeem(
                100,
                100,
                new BigDecimal("-5.00")
            );

        assertThat(redemption.rewardUnits())
            .isEqualTo(0);
        assertThat(redemption.rewardValue())
            .isEqualByComparingTo("0.00");
    }

    @Test
    void redeemRewardValueRoundsHalFUp() {

        LoyaltyRedemption redemption =
            LoyaltyCalculator.redeem(
                300,
                100,
                new BigDecimal("2.33")
            );

        assertThat(redemption.rewardUnits())
            .isEqualTo(3);
        assertThat(redemption.usedPoints())
            .isEqualTo(300);
        assertThat(redemption.rewardValue())
            .isEqualByComparingTo("6.99");
    }
}