package com.sup2i.food.promotion.api.dto;

import com.sup2i.food.promotion.domain.PromotionStatus;
import com.sup2i.food.promotion.domain.PromotionType;
import com.sup2i.food.promotion.domain.TargetType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CouponResponse(
    UUID id,
    String code,
    String name,
    String description,
    PromotionType type,
    PromotionStatus status,
    BigDecimal discountValue,
    BigDecimal maxDiscountAmount,
    Integer minQuantity,
    Integer usageLimitTotal,
    Integer usageLimitPerStudent,
    TargetType targetType,
    List<UUID> targetIds,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    boolean active,
    boolean mobileEnabled
) {
}