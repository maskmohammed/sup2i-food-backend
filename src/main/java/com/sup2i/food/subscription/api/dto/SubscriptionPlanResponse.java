package com.sup2i.food.subscription.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SubscriptionPlanResponse(
    UUID id,
    String name,
    String code,
    String billingPeriod,
    BigDecimal price,
    Integer includedMeals,
    boolean active
) {
}