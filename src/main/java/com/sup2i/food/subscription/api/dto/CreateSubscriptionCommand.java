package com.sup2i.food.subscription.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateSubscriptionCommand(

    @NotNull
    UUID studentId,

    @NotNull
    UUID planVersionId,

    @NotNull
    LocalDate startsAt,

    @NotNull
    LocalDate endsAt,

    @Size(max = 160)
    String paymentReference
) {
}