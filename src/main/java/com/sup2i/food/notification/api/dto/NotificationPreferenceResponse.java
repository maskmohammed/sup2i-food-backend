package com.sup2i.food.notification.api.dto;

import com.sup2i.food.notification.domain.NotificationCategory;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationPreferenceResponse(
    UUID id,
    UUID userId,
    NotificationCategory category,
    boolean pushEnabled,
    boolean emailEnabled,
    boolean inAppEnabled,
    LocalTime quietHoursStart,
    LocalTime quietHoursEnd,
    OffsetDateTime updatedAt,
    boolean persisted
) {
}