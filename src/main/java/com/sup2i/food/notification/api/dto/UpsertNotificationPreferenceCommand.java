package com.sup2i.food.notification.api.dto;

import java.time.LocalTime;

public record UpsertNotificationPreferenceCommand(
    boolean pushEnabled,
    boolean emailEnabled,
    boolean inAppEnabled,
    LocalTime quietHoursStart,
    LocalTime quietHoursEnd
) {
}