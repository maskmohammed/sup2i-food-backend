package com.sup2i.food.subscription.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateSubscriptionPlanCommand(

    @NotBlank
    @Size(max = 150)
    String name,

    @NotBlank
    @Size(max = 80)
    String code,

    @NotBlank
    String audienceType,

    @NotBlank
    String billingPeriod,

    @NotNull
    @DecimalMin("0.00")
    BigDecimal price,

    @Min(1)
    Integer includedMeals,

    @Min(1)
    Integer validityDays,

    String quotaType,

    @NotBlank
    String quotaPeriodType,

    @Min(1)
    Integer quotaValue,

    @NotNull
    @Min(1)
    Integer maxPerDay,

    @NotNull
    @Size(min = 1)
    List<@NotNull Integer> allowedDays,

    @NotNull
    @Size(min = 1)
    List<@NotBlank String> services,

    @NotNull
    Boolean reservationRequired,

    LocalTime reservationDeadline,

    LocalTime reservationCancellationDeadline,

    @NotBlank
    String renewalPolicy,

    @NotBlank
    String suspensionPolicy,

    UUID academicCalendarId,

    OffsetDateTime saleStartsAt,

    OffsetDateTime saleEndsAt,

    @NotNull
    Map<String, Object> rules
) {
}