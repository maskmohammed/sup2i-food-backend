package com.sup2i.food.promotion.api.dto;

import com.sup2i.food.promotion.domain.TargetType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UpdateCouponRequest(

    @NotBlank
    @Size(max = 180)
    String name,

    @NotNull
    @DecimalMin("0.01")
    BigDecimal discountValue,

    @DecimalMin("0.01")
    BigDecimal maxDiscountAmount,

    @NotNull
    TargetType targetType,

    List<UUID> targetIds,

    @Min(1)
    Integer minQuantity,

    @Min(1)
    Integer usageLimitTotal,

    @Min(1)
    Integer usageLimitPerStudent,

    @NotNull
    OffsetDateTime startsAt,

    @NotNull
    OffsetDateTime endsAt,

    @Size(max = 2000)
    String description
) {
}