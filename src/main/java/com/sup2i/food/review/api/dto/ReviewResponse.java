package com.sup2i.food.review.api.dto;

import com.sup2i.food.review.domain.ReviewTargetType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ReviewResponse(
    UUID id,
    UUID studentId,
    ReviewTargetType targetType,
    UUID targetId,
    int rating,
    String comment,
    OffsetDateTime createdAt,
    boolean replayed
) {
}