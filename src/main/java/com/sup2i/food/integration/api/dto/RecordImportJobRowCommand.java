package com.sup2i.food.integration.api.dto;

import java.util.UUID;

public record RecordImportJobRowCommand(
    UUID importJobId,
    int rowNumber,
    String rawDataJson
) {
}