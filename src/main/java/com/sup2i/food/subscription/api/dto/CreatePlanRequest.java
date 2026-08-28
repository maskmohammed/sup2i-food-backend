package com.sup2i.food.subscription.api.dto;

import com.sup2i.food.common.domain.MealType;
import com.sup2i.food.subscription.domain.AudienceType;
import com.sup2i.food.subscription.domain.BillingPeriod;
import com.sup2i.food.subscription.domain.QuotaPeriodType;
import com.sup2i.food.subscription.domain.RenewalPolicy;
import com.sup2i.food.subscription.domain.SuspensionPolicy;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Set;

public record CreatePlanRequest(

    @NotBlank
    @Size(max = 150)
    String name,

    @NotBlank
    @Size(max = 80)
    String code,

    @NotNull
    BillingPeriod billingPeriod,

    @NotNull
    @DecimalMin("0.00")
    @Digits(
        integer = 10,
        fraction = 2
    )
    BigDecimal price,

    @NotEmpty
    Set<MealType> services,

    @Min(1)
    Integer includedMeals,

    @Min(1)
    Integer validityDays,

    String quotaType,

    @Min(1)
    Integer quotaValue,

    @Min(1)
    Integer maxPerDay,

    Short[] allowedDays,

    boolean reservationRequired,

    QuotaPeriodType quotaPeriodType,

    RenewalPolicy renewalPolicy,

    SuspensionPolicy suspensionPolicy,

    AudienceType audienceType
) {
}
