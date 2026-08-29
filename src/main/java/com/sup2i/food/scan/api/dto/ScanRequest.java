package com.sup2i.food.scan.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record ScanRequest(

    @NotBlank
    String rawValue,

    UUID terminalId
) {
}