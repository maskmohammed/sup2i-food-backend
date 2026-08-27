package com.sup2i.food.qr.api.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ScanResolveResponse(
    String type,
    UUID referenceId,
    List<String> allowedActions,
    Map<String, Object> details
) {
}
