package com.sup2i.food.notification.api.dto;

import com.sup2i.food.notification.domain.NotificationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationItemResponse(
    UUID id,
    String type,
    String title,
    String body,
    NotificationStatus status,
    OffsetDateTime createdAt,
    OffsetDateTime readAt
) {
}