package com.sup2i.food.order.api.dto;

import com.sup2i.food.order.domain.OrderStatus;
import com.sup2i.food.order.domain.OrderStatusHistorySource;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderStatusHistoryResponse(
    UUID id,
    OrderStatus fromStatus,
    OrderStatus toStatus,
    UUID changedBy,
    String reason,
    OrderStatusHistorySource source,
    OffsetDateTime createdAt
) {
}