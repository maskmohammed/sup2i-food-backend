package com.sup2i.food.subscription.api.dto;

public record SubscriptionEntitlementResponse(
    String mealType,
    Long totalQuota,
    long usedQuota,
    Long remainingQuota
) {
}