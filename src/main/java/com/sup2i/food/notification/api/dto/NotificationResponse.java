package com.sup2i.food.notification.api.dto;

import com.sup2i.food.notification.domain.NotificationChannel;
import com.sup2i.food.notification.domain.NotificationStatus;
import com.sup2i.food.notification.domain.NotificationType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    NotificationType type,
    NotificationChannel channel,
    String title,
    String body,
    String payload,
    NotificationStatus status,
    OffsetDateTime sentAt,
    OffsetDateTime readAt,
    OffsetDateTime createdAt
) {
}
