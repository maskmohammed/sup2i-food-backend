package com.sup2i.food.order.api.dto;

public record QueueEstimateResponse(
    int ordersAhead,
    int estimatedMinutes
) {

    public QueueEstimateResponse {

        if (ordersAhead < 0) {
            throw new IllegalArgumentException(
                "ordersAhead cannot be negative."
            );
        }

        if (estimatedMinutes < 0) {
            throw new IllegalArgumentException(
                "estimatedMinutes cannot be negative."
            );
        }
    }
}