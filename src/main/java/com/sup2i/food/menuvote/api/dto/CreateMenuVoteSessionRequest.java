package com.sup2i.food.menuvote.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record CreateMenuVoteSessionRequest(
    @NotBlank
    @Size(max = 180)
    String title,

    @Size(max = 4000)
    String description,

    @NotNull
    LocalDate targetWeek,

    @NotNull
    OffsetDateTime voteDeadline,

    @NotEmpty
    @Valid
    List<MenuVoteOptionRequest> options
) {
}