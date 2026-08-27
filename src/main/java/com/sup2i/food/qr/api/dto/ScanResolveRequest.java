package com.sup2i.food.qr.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ScanResolveRequest(

    @NotBlank
    String token
) {
}
