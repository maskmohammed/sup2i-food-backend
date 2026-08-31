package com.sup2i.food.notification.api.dto;

import com.sup2i.food.notification.domain.NotificationChannel;
import com.sup2i.food.notification.domain.NotificationPriority;

import java.time.OffsetDateTime;

public record CreateNotificationCommand(
    String type,
    NotificationChannel channel,
    String title,
    String body,
    String payloadJson,
    NotificationPriority priority,
    String deduplicationKey,
    OffsetDateTime scheduledAt
) {
}