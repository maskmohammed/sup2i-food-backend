package com.sup2i.food.promotion.api.dto;

import java.math.BigDecimal;

public record CouponValidationResponse(
    boolean eligible,
    String reason,
    CouponSummary coupon,
    BigDecimal eligibleAmount,
    BigDecimal discountAmount
) {
}