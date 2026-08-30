package com.sup2i.food.loyalty.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record LoyaltyResponse(
    int balance,
    List<TransactionResponse> transactions
) {

    public record TransactionResponse(
        String type,
        int points,
        String reason,
        OffsetDateTime createdAt
    ) {
    }
}
