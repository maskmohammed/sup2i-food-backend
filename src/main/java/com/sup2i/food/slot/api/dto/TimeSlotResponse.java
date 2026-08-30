package com.sup2i.food.slot.api.dto;

import com.sup2i.food.slot.domain.TimeSlotStatus;

import java.time.LocalDate;
import java.util.UUID;

public record TimeSlotResponse(
    UUID id,
    LocalDate date,
    String startTime,
    String endTime,
    int capacity,
    int reservedCount,
    int remainingCapacity,
    TimeSlotStatus status
) {
}