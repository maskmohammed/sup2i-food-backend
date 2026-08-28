package com.sup2i.food.promotion.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ApplyCouponResponse(
    UUID orderId,
    String couponCode,
    BigDecimal discountAmount,
    BigDecimal discountTotal,
    BigDecimal subtotal,
    BigDecimal total
) {
}