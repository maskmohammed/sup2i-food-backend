package com.sup2i.food.kitchen.api.dto;

import com.sup2i.food.kitchen.domain.KitchenTicketStatus;
import com.sup2i.food.order.api.dto.OrderItemResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record KitchenTicketResponse(
    UUID id,
    UUID orderId,
    String orderNumber,
    KitchenTicketStatus status,
    int priority,
    OffsetDateTime queuedAt,
    OffsetDateTime startedAt,
    OffsetDateTime readyAt,
    List<OrderItemResponse> items,
    List<KitchenTicketLineResponse> lines
) {
}
