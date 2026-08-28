package com.sup2i.food.menuvote.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record VoteRequest(
    @NotNull
    UUID optionId
) {
}