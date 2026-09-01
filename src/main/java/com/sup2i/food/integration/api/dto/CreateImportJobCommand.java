package com.sup2i.food.integration.api.dto;

import com.sup2i.food.integration.domain.ImportType;

import java.util.UUID;

public record CreateImportJobCommand(
    ImportType importType,
    UUID sourceFileAssetId,
    UUID requestedBy
) {
}