package com.sup2i.food.promotion.service;

import java.math.BigDecimal;

public record CouponEvaluation(
    BigDecimal eligibleAmount,
    BigDecimal discountAmount
) {
}