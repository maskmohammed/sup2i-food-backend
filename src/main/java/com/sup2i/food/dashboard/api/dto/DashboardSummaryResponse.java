package com.sup2i.food.dashboard.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardSummaryResponse(
    RevenueSummaryResponse revenue,
    BigDecimal averageBasket,
    List<OrderStatusCountResponse> ordersByStatus,
    List<TopProductResponse> topProducts,
    Double averagePreparationMinutes
) {
}
