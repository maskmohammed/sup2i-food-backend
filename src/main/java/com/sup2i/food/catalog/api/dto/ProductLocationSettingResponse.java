package com.sup2i.food.catalog.api.dto;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record ProductLocationSettingResponse(
    UUID id,
    UUID locationId,
    String locationName,
    boolean enabled,
    List<Integer> allowedDays,
    LocalTime availableFromTime,
    LocalTime availableToTime,
    Integer preparationMinutes
) {
}