package com.sup2i.food.review.api.dto;

import com.sup2i.food.security.validation.SafeText;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateReviewRequest(
    UUID productId,
    UUID orderId,

    @Min(1)
    @Max(5)
    int rating,

    @SafeText
    @Size(max = 2000)
    String comment,

    List<@Size(max = 500) String> photos
) {
}