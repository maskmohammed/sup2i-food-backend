package com.sup2i.food.subscription.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SubscriptionResponse(
    UUID id,
    SubscriptionPlanResponse plan,
    String status,
    LocalDate startsAt,
    LocalDate endsAt,
    List<SubscriptionEntitlementResponse> entitlements
) {
}