package com.sup2i.food.promotion.service;

import com.sup2i.food.promotion.domain.PromotionType;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CouponCalculator {

    private CouponCalculator() {
    }

    public static CouponEvaluation evaluate(
        PromotionType type,
        BigDecimal discountValue,
        BigDecimal maxDiscountAmount,
        BigDecimal eligibleAmount
    ) {
        if (
            discountValue == null
                || eligibleAmount == null
                || eligibleAmount.signum() <= 0
        ) {
            return new CouponEvaluation(
                BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2)
            );
        }

        BigDecimal eligible =
            eligibleAmount.setScale(
                2,
                RoundingMode.HALF_UP
            );

        BigDecimal discount;

        if (
            type == PromotionType.PERCENTAGE
        ) {
            discount =
                eligible
                    .multiply(discountValue)
                    .divide(
                        BigDecimal.valueOf(100),
                        2,
                        RoundingMode.HALF_UP
                    );
        } else {
            discount =
                discountValue.setScale(
                    2,
                    RoundingMode.HALF_UP
                );
        }

        if (
            discount.signum() <= 0
        ) {
            return new CouponEvaluation(
                eligible,
                BigDecimal.ZERO.setScale(2)
            );
        }

        if (
            discount.compareTo(eligible) > 0
        ) {
            discount =
                eligible;
        }

        if (
            maxDiscountAmount != null
        ) {
            BigDecimal cap =
                maxDiscountAmount.setScale(
                    2,
                    RoundingMode.HALF_UP
                );

            if (
                discount.compareTo(cap) > 0
            ) {
                discount =
                    cap;
            }
        }

        return new CouponEvaluation(
            eligible,
            discount
        );
    }
}