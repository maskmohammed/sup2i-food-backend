package com.sup2i.food.timeslot.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record TimeSlotResponse(
    UUID id,
    UUID locationId,
    LocalDate slotDate,
    LocalTime startTime,
    LocalTime endTime,
    int capacity,
    int reservedCount,
    int remainingCapacity,
    String status
) {
}
