package com.sup2i.food.subscription.api.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AdminSubscriptionPlanResponse(
    UUID id,
    UUID planVersionId,
    int versionNumber,
    String name,
    String code,
    String audienceType,
    String billingPeriod,
    BigDecimal price,
    Integer includedMeals,
    Integer validityDays,
    String quotaType,
    String quotaPeriodType,
    Integer quotaValue,
    Integer maxPerDay,
    List<Integer> allowedDays,
    List<String> services,
    boolean reservationRequired,
    LocalTime reservationDeadline,
    LocalTime reservationCancellationDeadline,
    String renewalPolicy,
    String suspensionPolicy,
    UUID academicCalendarId,
    OffsetDateTime saleStartsAt,
    OffsetDateTime saleEndsAt,
    Map<String, Object> rules,
    boolean active
) {
}