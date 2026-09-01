package com.sup2i.food.integration.api.dto;

import com.sup2i.food.integration.domain.IntegrationConnectorType;
import com.sup2i.food.integration.domain.IntegrationDirection;

public record CreateIntegrationConnectorCommand(
    String code,
    IntegrationConnectorType connectorType,
    IntegrationDirection direction,
    String configJson,
    String secretRef
) {
}