package com.sup2i.food.canteen.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MealUsageResponse(
    UUID id,
    UUID studentId,
    String mealType,
    LocalDate usageDate,
    OffsetDateTime consumedAt,
    Long remainingQuota
) {
}
