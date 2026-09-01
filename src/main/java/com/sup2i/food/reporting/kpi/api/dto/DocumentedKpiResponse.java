package com.sup2i.food.reporting.kpi.api.dto;

import java.math.BigDecimal;

public record DocumentedKpiResponse(
    BigDecimal averageBasket,
    BigDecimal preorderRate,
    BigDecimal canteenUsageRate,
    BigDecimal wasteQuantityRate,
    BigDecimal wasteValueRate,
    BigDecimal estimatedGrossMaterialMargin
) {
}