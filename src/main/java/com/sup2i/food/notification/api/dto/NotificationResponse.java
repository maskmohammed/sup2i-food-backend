package com.sup2i.food.notification.api.dto;

import com.sup2i.food.notification.domain.NotificationChannel;
import com.sup2i.food.notification.domain.NotificationPriority;
import com.sup2i.food.notification.domain.NotificationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    UUID userId,
    String type,
    NotificationChannel channel,
    String title,
    String body,
    String payloadJson,
    NotificationStatus status,
    NotificationPriority priority,
    String deduplicationKey,
    OffsetDateTime scheduledAt,
    int retryCount,
    String lastError,
    OffsetDateTime sentAt,
    OffsetDateTime readAt,
    OffsetDateTime createdAt,
    boolean replayed
) {
}