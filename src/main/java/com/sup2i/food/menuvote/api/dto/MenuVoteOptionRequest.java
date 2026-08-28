package com.sup2i.food.menuvote.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record MenuVoteOptionRequest(
    UUID productId,

    @NotBlank
    @Size(max = 180)
    String label,

    @Size(max = 2000)
    String description
) {
}