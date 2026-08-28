package com.sup2i.food.notification.api.dto;

public record NotificationMutationResponse(
    NotificationResponse notification,
    boolean replayed
) {
}
