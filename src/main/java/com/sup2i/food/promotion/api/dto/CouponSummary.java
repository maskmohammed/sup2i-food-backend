package com.sup2i.food.promotion.api.dto;

import com.sup2i.food.promotion.domain.PromotionType;
import com.sup2i.food.promotion.domain.TargetType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CouponSummary(
    UUID id,
    String code,
    String label,
    PromotionType type,
    BigDecimal discountValue,
    BigDecimal maxDiscountAmount,
    TargetType targetType,
    List<UUID> targetIds,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt
) {
}