package com.sup2i.food.order.api.dto;

import java.time.OffsetDateTime;

public record ReadyOrderResponse(
    String orderNumber,
    OffsetDateTime readyAt
) {
}