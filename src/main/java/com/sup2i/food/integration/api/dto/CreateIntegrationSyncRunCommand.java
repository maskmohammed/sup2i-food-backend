package com.sup2i.food.integration.api.dto;

import java.util.UUID;

public record CreateIntegrationSyncRunCommand(
    UUID connectorId,
    String syncType,
    UUID initiatedBy
) {
}