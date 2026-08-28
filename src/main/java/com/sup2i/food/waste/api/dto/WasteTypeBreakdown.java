package com.sup2i.food.waste.api.dto;

import com.sup2i.food.waste.domain.WasteType;

import java.math.BigDecimal;

public record WasteTypeBreakdown(
    WasteType wasteType,
    BigDecimal totalQuantity,
    long recordCount
) {
}