package com.sup2i.food.kitchen.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record KitchenTicketItemResponse(
    UUID id,
    UUID orderItemId,
    String productName,
    BigDecimal quantity,
    String status
) {
}
