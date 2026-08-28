package com.sup2i.food.review.api.dto;

import com.sup2i.food.review.domain.ModerationStatus;
import com.sup2i.food.review.domain.Review;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReviewResponse(
    UUID id,
    UUID productId,
    UUID orderId,
    int rating,
    String comment,
    List<String> photos,
    ModerationStatus moderationStatus,
    UUID studentUserId,
    UUID studentId,
    OffsetDateTime createdAt
) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
            review.getId(),
            review.getProduct() == null ? null : review.getProduct().getId(),
            review.getOrder() == null ? null : review.getOrder().getId(),
            review.getRating(),
            review.getComment(),
            ReviewJson.photos(review),
            review.getModerationStatus(),
            review.getStudent().getUser().getId(),
            review.getStudent().getId(),
            review.getCreatedAt()
        );
    }
}