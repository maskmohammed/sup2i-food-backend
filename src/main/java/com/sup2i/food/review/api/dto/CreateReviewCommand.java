package com.sup2i.food.review.api.dto;

import com.sup2i.food.review.domain.ReviewTargetType;

import java.util.UUID;

public record CreateReviewCommand(
    ReviewTargetType targetType,
    UUID targetId,
    int rating,
    String comment
) {
}