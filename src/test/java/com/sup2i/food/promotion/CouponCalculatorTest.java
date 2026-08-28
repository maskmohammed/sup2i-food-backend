package com.sup2i.food.promotion;

import com.sup2i.food.promotion.domain.PromotionType;
import com.sup2i.food.promotion.service.CouponCalculator;
import com.sup2i.food.promotion.service.CouponEvaluation;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CouponCalculatorTest {

    @Test
    void percentageDiscountComputesValue() {

        CouponEvaluation evaluation =
            CouponCalculator.evaluate(
                PromotionType.PERCENTAGE,
                new BigDecimal("20"),
                null,
                new BigDecimal("100.00")
            );

        assertThat(evaluation.eligibleAmount())
            .isEqualByComparingTo("100.00");
        assertThat(evaluation.discountAmount())
            .isEqualByComparingTo("20.00");
    }

    @Test
    void percentageDiscountRespectsMaximumCap() {

        CouponEvaluation evaluation =
            CouponCalculator.evaluate(
                PromotionType.PERCENTAGE,
                new BigDecimal("20"),
                new BigDecimal("10.00"),
                new BigDecimal("100.00")
            );

        assertThat(evaluation.discountAmount())
            .isEqualByComparingTo("10.00");
    }

    @Test
    void percentageDiscountRoundsHalFUp() {

        CouponEvaluation evaluation =
            CouponCalculator.evaluate(
                PromotionType.PERCENTAGE,
                new BigDecimal("50"),
                null,
                new BigDecimal("42.25")
            );

        assertThat(evaluation.discountAmount())
            .isEqualByComparingTo("21.13");
    }

    @Test
    void fixedAmountWithinEligibleIsKept() {

        CouponEvaluation evaluation =
            CouponCalculator.evaluate(
                PromotionType.FIXED_AMOUNT,
                new BigDecimal("15.00"),
                null,
                new BigDecimal("20.00")
            );

        assertThat(evaluation.discountAmount())
            .isEqualByComparingTo("15.00");
    }

    @Test
    void fixedAmountAboveEligibleIsCappedToEligible() {

        CouponEvaluation evaluation =
            CouponCalculator.evaluate(
                PromotionType.FIXED_AMOUNT,
                new BigDecimal("25.00"),
                null,
                new BigDecimal("20.00")
            );

        assertThat(evaluation.discountAmount())
            .isEqualByComparingTo("20.00");
    }

    @Test
    void nullEligibleAmountReturnsZero() {

        CouponEvaluation evaluation =
            CouponCalculator.evaluate(
                PromotionType.PERCENTAGE,
                new BigDecimal("20"),
                null,
                null
            );

        assertThat(evaluation.eligibleAmount())
            .isEqualByComparingTo("0.00");
        assertThat(evaluation.discountAmount())
            .isEqualByComparingTo("0.00");
    }

    @Test
    void nullDiscountValueReturnsZero() {

        CouponEvaluation evaluation =
            CouponCalculator.evaluate(
                PromotionType.PERCENTAGE,
                null,
                null,
                new BigDecimal("100.00")
            );

        assertThat(evaluation.discountAmount())
            .isEqualByComparingTo("0.00");
    }

    @Test
    void nonPositiveEligibleAmountReturnsZero() {

        CouponEvaluation evaluation =
            CouponCalculator.evaluate(
                PromotionType.PERCENTAGE,
                new BigDecimal("20"),
                null,
                BigDecimal.ZERO
            );

        assertThat(evaluation.eligibleAmount())
            .isEqualByComparingTo("0.00");
        assertThat(evaluation.discountAmount())
            .isEqualByComparingTo("0.00");
    }

    @Test
    void zeroPercentDiscountYieldsNoDiscount() {

        CouponEvaluation evaluation =
            CouponCalculator.evaluate(
                PromotionType.PERCENTAGE,
                BigDecimal.ZERO,
                null,
                new BigDecimal("100.00")
            );

        assertThat(evaluation.eligibleAmount())
            .isEqualByComparingTo("100.00");
        assertThat(evaluation.discountAmount())
            .isEqualByComparingTo("0.00");
    }
}