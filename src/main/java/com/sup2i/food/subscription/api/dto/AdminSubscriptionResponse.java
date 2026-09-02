package com.sup2i.food.subscription.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AdminSubscriptionResponse(
    UUID id,
    UUID studentId,
    UUID planId,
    UUID planVersionId,
    String status,
    LocalDate startsAt,
    LocalDate endsAt,
    String paymentReference,
    List<EntitlementResponse> entitlements
) {

    public record EntitlementResponse(
        UUID id,
        String mealType,
        LocalDate validFrom,
        LocalDate validTo,
        List<Integer> allowedDays,
        Integer totalQuota,
        int dailyLimit,
        String quotaPeriodType,
        boolean reservationRequired
    ) {
    }
}