package com.sup2i.food.kitchen.api.dto;

import com.sup2i.food.kitchen.domain.KitchenTicketItemStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record KitchenTicketLineResponse(
    UUID id,
    UUID orderItemId,
    UUID menuSelectionId,
    UUID productId,
    UUID variantId,
    String productName,
    String variantName,
    BigDecimal quantity,
    KitchenTicketItemStatus status,
    String specialInstructions,
    OffsetDateTime startedAt,
    OffsetDateTime readyAt,
    OffsetDateTime cancelledAt,
    String issueNote
) {
}
