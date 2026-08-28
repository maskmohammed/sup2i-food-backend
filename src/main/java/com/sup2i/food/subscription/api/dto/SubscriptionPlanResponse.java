package com.sup2i.food.subscription.api.dto;

import com.sup2i.food.common.domain.MealType;
import com.sup2i.food.subscription.domain.AudienceType;
import com.sup2i.food.subscription.domain.BillingPeriod;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record SubscriptionPlanResponse(
    UUID id,
    String name,
    String code,
    BillingPeriod billingPeriod,
    BigDecimal price,
    Integer includedMeals,
    Integer validityDays,
    Integer quotaValue,
    Integer maxPerDay,
    boolean reservationRequired,
    boolean active,
    AudienceType audienceType,
    Set<MealType> services,
    UUID currentVersionId,
    int currentVersionNumber
) {
}
