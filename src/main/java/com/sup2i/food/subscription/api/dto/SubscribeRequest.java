package com.sup2i.food.subscription.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SubscribeRequest(

    @NotNull
    UUID planId
) {
}
