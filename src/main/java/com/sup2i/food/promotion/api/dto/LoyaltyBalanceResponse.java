package com.sup2i.food.promotion.api.dto;

public record LoyaltyBalanceResponse(
    String status,
    int balance,
    int lifetimeEarned,
    int lifetimeRedeemed
) {
}