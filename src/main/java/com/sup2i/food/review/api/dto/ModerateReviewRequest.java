package com.sup2i.food.review.api.dto;

import com.sup2i.food.review.domain.ModerationStatus;
import jakarta.validation.constraints.NotNull;

public record ModerateReviewRequest(
    @NotNull
    ModerationStatus status
) {
}