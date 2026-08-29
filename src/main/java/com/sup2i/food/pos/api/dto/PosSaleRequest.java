package com.sup2i.food.pos.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record PosSaleRequest(

    @NotNull
    UUID posSessionId,

    @Size(max = 2000)
    String customerNote,

    @NotNull
    @Size(min = 1, max = 50)
    List<
        @Valid
        PosSaleItemRequest
    > items
) {
}