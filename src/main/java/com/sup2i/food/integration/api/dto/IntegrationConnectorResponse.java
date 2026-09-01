package com.sup2i.food.integration.api.dto;

import com.sup2i.food.integration.domain.IntegrationConnectorStatus;
import com.sup2i.food.integration.domain.IntegrationConnectorType;
import com.sup2i.food.integration.domain.IntegrationDirection;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IntegrationConnectorResponse(
    UUID id,
    UUID organizationId,
    String code,
    IntegrationConnectorType connectorType,
    IntegrationDirection direction,
    IntegrationConnectorStatus status,
    String configJson,
    boolean secretReferenceConfigured,
    OffsetDateTime lastSuccessAt,
    OffsetDateTime lastErrorAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    boolean replayed
) {
}