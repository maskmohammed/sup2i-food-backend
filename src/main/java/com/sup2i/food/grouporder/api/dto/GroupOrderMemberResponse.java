package com.sup2i.food.grouporder.api.dto;

import com.sup2i.food.grouporder.domain.GroupOrderMemberStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record GroupOrderMemberResponse(
    UUID id,
    UUID groupOrderId,
    UUID studentId,
    GroupOrderMemberStatus status,
    OffsetDateTime joinedAt,
    OffsetDateTime leftAt,
    boolean replayed
) {
}