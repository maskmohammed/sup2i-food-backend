package com.sup2i.food.notification.api.dto;

import java.util.List;

public record PagedNotificationsResponse(
    List<NotificationItemResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
}