package com.sup2i.food.waste.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record WasteStatsResponse(
    long recordCount,
    BigDecimal totalQuantity,
    BigDecimal totalCost,
    List<WasteTypeBreakdown> byType
) {
}