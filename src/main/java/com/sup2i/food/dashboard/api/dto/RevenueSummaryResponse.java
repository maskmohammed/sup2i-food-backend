package com.sup2i.food.dashboard.api.dto;

import java.math.BigDecimal;

public record RevenueSummaryResponse(
    BigDecimal today,
    BigDecimal thisWeek,
    BigDecimal thisMonth
) {
}
