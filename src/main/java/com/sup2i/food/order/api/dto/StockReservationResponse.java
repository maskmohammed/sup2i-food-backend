package com.sup2i.food.order.api.dto;

import com.sup2i.food.order.domain.StockReservationStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record StockReservationResponse(
    UUID id,
    UUID orderItemId,
    UUID stockItemId,
    UUID stockLocationId,
    BigDecimal quantity,
    StockReservationStatus status,
    OffsetDateTime expiresAt,
    OffsetDateTime createdAt,
    OffsetDateTime consumedAt,
    OffsetDateTime releasedAt
) {
}