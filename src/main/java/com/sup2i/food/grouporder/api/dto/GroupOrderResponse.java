package com.sup2i.food.grouporder.api.dto;

import com.sup2i.food.grouporder.domain.GroupOrderStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record GroupOrderResponse(
    UUID id,
    UUID orderId,
    UUID ownerStudentId,
    String joinCode,
    GroupOrderStatus status,
    OffsetDateTime closesAt,
    OffsetDateTime createdAt,
    boolean replayed
) {
}