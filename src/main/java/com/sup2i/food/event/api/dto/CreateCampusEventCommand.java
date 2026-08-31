package com.sup2i.food.event.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateCampusEventCommand(
    UUID campusId,
    String name,
    String eventType,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    Integer expectedAttendance,
    String description
) {
}