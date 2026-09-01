package com.sup2i.food.reporting.kpi.api.dto;

import java.math.BigDecimal;

public record DocumentedKpiInput(
    BigDecimal snackRevenue,
    long paidSnackTransactions,
    long mobilePreorderedSnackOrders,
    long totalSnackOrders,
    long mealsDistributed,
    long availableMealRights,
    BigDecimal wastedQuantity,
    BigDecimal preparedQuantity,
    BigDecimal estimatedWasteCost,
    BigDecimal consumedMaterialCost,
    BigDecimal totalRevenue
) {
}