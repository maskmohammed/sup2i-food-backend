package com.sup2i.food.subscription.api.dto;

public record SubscriptionMutationResponse(
    SubscriptionResponse subscription,
    boolean replayed
) {
}
