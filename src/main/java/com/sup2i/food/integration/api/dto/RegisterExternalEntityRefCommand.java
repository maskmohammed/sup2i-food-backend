package com.sup2i.food.integration.api.dto;

import java.util.UUID;

public record RegisterExternalEntityRefCommand(
    UUID connectorId,
    String localEntityType,
    UUID localEntityId,
    String externalEntityType,
    String externalId,
    String externalVersion,
    String metadataJson
) {
}