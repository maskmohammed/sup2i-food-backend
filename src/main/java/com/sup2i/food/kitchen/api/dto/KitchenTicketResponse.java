package com.sup2i.food.kitchen.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record KitchenTicketResponse(
    UUID id,
    UUID orderId,
    String orderNumber,
    UUID kitchenLocationId,
    String status,
    int priority,
    OffsetDateTime queuedAt,
    OffsetDateTime acceptedAt,
    OffsetDateTime startedAt,
    OffsetDateTime readyAt,
    List<KitchenTicketItemResponse> items
) {
}
