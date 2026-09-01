package com.sup2i.food.interaction.api.dto;

import com.sup2i.food.interaction.domain.ProductInteractionType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductInteractionResponse(
    UUID id,
    UUID studentId,
    UUID productId,
    ProductInteractionType eventType,
    UUID cartId,
    UUID orderId,
    UUID locationId,
    OffsetDateTime occurredAt,
    String metadataJson,
    boolean replayed
) {
}