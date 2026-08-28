package com.sup2i.food.subscription.api.dto;

import com.sup2i.food.subscription.domain.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SubscriptionResponse(
    UUID id,
    UUID studentId,
    UUID planId,
    String planName,
    UUID planVersionId,
    SubscriptionStatus status,
    LocalDate startsAt,
    LocalDate endsAt,
    String paymentReference,
    BigDecimal administrativePaymentAmount,
    OffsetDateTime activatedAt,
    OffsetDateTime suspendedAt,
    OffsetDateTime cancelledAt,
    List<MealEntitlementResponse> entitlements
) {
}
