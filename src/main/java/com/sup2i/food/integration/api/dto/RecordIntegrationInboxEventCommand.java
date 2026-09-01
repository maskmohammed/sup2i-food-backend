package com.sup2i.food.integration.api.dto;

import java.util.UUID;

public record RecordIntegrationInboxEventCommand(
    UUID connectorId,
    String externalEventId,
    String eventType,
    String payloadJson
) {
}