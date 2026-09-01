package com.sup2i.food.integration.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ExternalEntityRefResponse(
    UUID id,
    UUID connectorId,
    String localEntityType,
    UUID localEntityId,
    String externalEntityType,
    String externalId,
    String externalVersion,
    OffsetDateTime lastSyncedAt,
    String metadataJson,
    boolean replayed
) {
}