package com.sup2i.food.subscription.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SubscriptionStatusHistoryResponse(
    UUID id,
    String fromStatus,
    String toStatus,
    UUID changedById,
    String reason,
    OffsetDateTime createdAt
) {
}