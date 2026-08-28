package com.sup2i.food.dashboard.api.dto;

public record OrderStatusCountResponse(
    String status,
    long count
) {
}
