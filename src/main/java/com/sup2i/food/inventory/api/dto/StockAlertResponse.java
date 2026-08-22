package com.sup2i.food.inventory.api.dto;

import com.sup2i.food.inventory.domain.StockAlertSeverity;
import com.sup2i.food.inventory.domain.StockAlertStatus;
import com.sup2i.food.inventory.domain.StockAlertType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record StockAlertResponse(
    UUID id,
    UUID stockItemId,
    UUID stockLocationId,
    StockAlertType alertType,
    StockAlertStatus status,
    StockAlertSeverity severity,
    BigDecimal thresholdValue,
    BigDecimal observedValue,
    UUID lotId,
    OffsetDateTime detectedAt,
    UUID acknowledgedBy,
    OffsetDateTime acknowledgedAt,
    OffsetDateTime resolvedAt
) {
}