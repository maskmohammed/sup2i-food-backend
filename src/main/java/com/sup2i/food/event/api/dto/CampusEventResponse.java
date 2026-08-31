package com.sup2i.food.event.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CampusEventResponse(
    UUID id,
    UUID campusId,
    String name,
    String eventType,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    Integer expectedAttendance,
    String description,
    UUID createdBy,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    boolean replayed
) {
}