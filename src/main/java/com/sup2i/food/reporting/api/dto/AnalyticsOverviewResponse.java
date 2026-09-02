package com.sup2i.food.reporting.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record AnalyticsOverviewResponse(
    BigDecimal revenue,
    long orders,
    BigDecimal averageBasket,
    BigDecimal mobileSales,
    BigDecimal posSales,
    long activeCanteenSubscriptions,
    long mealsDistributed,
    BigDecimal wasteCost,
    List<AnalyticsTopProductResponse> topProducts
) {
}